"""Validate tools/recovery.py against bad records (#317, from upstream).

The recovery record is replayed on the TV's shell, so a hand-edited or damaged JSON must refuse
to load before any command reaches the TV. This test runs the real recovery.py against a
fake tools/tv.py that answers `pm list packages`, `pm list packages -d`, the Home resolver, and
`settings get global ...` from a JSON state file. The fake is self-contained here so the test
works without the shared tests/fake_tv.py the home-guard and input-guard tests use.

Run from the repository root:
    python -m unittest tests.test_recovery_record -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
CUSTOM_HOME = "br.eddy.tvhome/.MainActivity"

# Stands in for tools/tv.py: answers what recovery.py asks. The --batch entry point records every
# line the batch sends so a test can check the commands.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]

with open(STATE_PATH, encoding="utf-8") as f:
    state = json.load(f)


def save():
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f)


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if "resolve-activity" in command:
        return (state.get("home") or "") + "\n"
    if command.startswith("settings get global "):
        key = command.split()[-1]
        return str(state.get("settings", {}).get(key, "null")) + "\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    if state.get("unreachable"):
        raise ConnectionError("could not connect to 192.168.1.50:5555")
    return Device()


def main():
    args = sys.argv[1:]
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                state["commands"].append(line)
        save()
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class RecoveryRecordTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        # The real scripts and the real recovery.py are copied in, unchanged. Only the ADB
        # transport (tools/tv.py) is faked; recovery.py's load_record() runs as in production.
        for name in ("disable.sh", "restore.sh", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV, encoding="utf-8")

        # disable.sh and restore.sh invoke tools/venv/bin/python. A tiny shell wrapper runs the
        # real interpreter so the fake tv.py is imported without needing a full repo venv.
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        python = sys.executable.replace("\\", "/")
        client.write_text(f'#!/bin/sh\nexec "{python}" "$@"\n', encoding="utf-8")
        client.chmod(0o755)

        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# test\ncom.example.one\ncom.example.two\n", encoding="utf-8")

    def tv(self, **overrides):
        state = {
            "installed": ["com.example.one", "com.example.two", STOCK_HOME.split("/", 1)[0]],
            "disabled": [],
            "home": STOCK_HOME,
            "settings": {"window_animation_scale": "0.5",
                         "transition_animation_scale": "null",
                         "animator_duration_scale": "1.0"},
            "commands": [],
            "unreachable": False,
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state), encoding="utf-8")

    def run_recovery(self, *args):
        # Path.home() on Windows reads USERPROFILE, not HOME; recovery.py uses Path.home() to
        # resolve ~/.cleanyourtv/, so both go to the same temp dir for the test.
        env = dict(os.environ, TV_HOST="192.168.1.50",
                   HOME=str(self.home), USERPROFILE=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        return subprocess.run(
            [sys.executable, str(self.repo / "tools" / "recovery.py"), *args],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=60,
        )

    def record(self):
        files = list((self.home / ".cleanyourtv").glob("recovery-*.json"))
        self.assertLessEqual(len(files), 1, "one record per TV and list")
        return json.loads(files[0].read_text()) if files else None

    def sent(self):
        with open(self.state_path, encoding="utf-8") as f:
            return json.load(f)["commands"]

    def write_record(self, mutate):
        """Run --save, then mutate the record on disk the way a hand edit or a damaged write
        might, so the next --commands call has to validate before replaying."""
        path = self.repo / "tools" / "recovery.py"
        self.assertEqual(self.run_recovery("--save", str(self.list_path)).returncode, 0)
        rec = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        record = json.loads(rec.read_text())
        mutate(record)
        rec.write_text(json.dumps(record))

    # --- a valid record replays and refuses bad input ---

    def test_save_writes_a_record_then_commands_replays_it(self):
        self.tv()
        save_result = self.run_recovery("--save", str(self.list_path))
        self.assertEqual(save_result.returncode, 0, save_result.stderr)
        record = self.record()
        self.assertIsNotNone(record)
        self.assertEqual(record["version"], 1)
        self.assertIn("com.example.one", record["packages"])
        self.assertEqual(record["home"], STOCK_HOME)

        self.tv()  # reset the commands list
        commands = self.run_recovery("--commands", str(self.list_path)).stdout.splitlines()
        self.assertIn("pm enable com.example.one", commands)
        self.assertIn("pm enable com.example.two", commands)
        self.assertIn(f"cmd package set-home-activity --user 0 {STOCK_HOME}", commands)
        self.assertIn("settings put global window_animation_scale 0.5", commands)
        self.assertIn("settings delete global transition_animation_scale", commands)

    def test_bad_records_are_refused_and_left_alone(self):
        """Each subTest writes a record, then mutates one field the way a hand edit or a
        damaged disk might, then asks for the batch. The script must refuse and the file must
        be on disk unchanged for inspection."""
        edits = {
            "package name":
                lambda r: r["packages"].update(
                    {"com.example.x; settings put global adb_enabled 0 #": "enabled"}),
            "package state":
                lambda r: r["packages"].update({"com.example.one": "on"}),
            "home":
                lambda r: r.update(home=f"{STOCK_HOME}; settings put global adb_enabled 0"),
            "settings":
                lambda r: r.update(settings=["window_animation_scale", "0.5"]),
        }
        for field, mutate in edits.items():
            with self.subTest(field=field):
                shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                self.tv()
                self.write_record(mutate)
                before = next((self.home / ".cleanyourtv").glob("recovery-*.json")).read_bytes()
                # Wipe any commands the --save run already pushed through.
                with open(self.state_path, encoding="utf-8") as f:
                    state = json.load(f)
                state["commands"] = []
                self.state_path.write_text(json.dumps(state), encoding="utf-8")

                result = self.run_recovery("--commands", str(self.list_path))
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("not a usable recovery record", result.stderr)
                after = next((self.home / ".cleanyourtv").glob("recovery-*.json")).read_bytes()
                self.assertEqual(before, after, "the file is left for inspection")
                self.assertEqual(self.sent(), [], "nothing reached the TV")

    def test_unreachable_tv_does_not_disable_anything(self):
        self.tv(unreachable=True)
        result = self.run_recovery("--save", str(self.list_path))
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIsNone(self.record(), "no record was written")
        self.assertEqual(self.sent(), [], "no command reached the fake TV")

    def test_unknown_version_is_refused(self):
        self.tv()
        self.assertEqual(self.run_recovery("--save", str(self.list_path)).returncode, 0)
        path = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        record = json.loads(path.read_text())
        record["version"] = 2  # a future version this script does not understand
        path.write_text(json.dumps(record))
        result = self.run_recovery("--commands", str(self.list_path))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("unknown recovery record", result.stderr)

    def test_commands_without_a_record_warns_and_uses_a_fallback(self):
        """A list run without a prior disable (an old list, a lost record) must still do
        something safe-ish: enable every package and reset everything, but warn about it."""
        self.tv()
        result = self.run_recovery("--commands", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("no recovery record", result.stderr)
        out = result.stdout.splitlines()
        self.assertIn("pm enable com.example.one", out)
        self.assertIn("pm enable com.example.two", out)
        self.assertIn("settings put global window_animation_scale 1.0", out)


if __name__ == "__main__":
    unittest.main()
