"""Home guard for the debloat scripts (#239/#350).

The scripts must not disable the app that owns Home while no other real HOME activity remains
enabled. `tools/tv.py` is replaced by a fake that answers the two package-manager queries; the real
`disable.sh` still runs, validates the list and builds the batch.

Run from the repository root:
    python -m unittest tests.test_home_guard -v
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
STOCK = "com.google.android.tvlauncher"
CUSTOM = "br.eddy.tvhome"
FALLBACK = "com.android.tv.settings/.system.FallbackHome"
GIT_BASH = Path(r"C:\Program Files\Git\bin\bash.exe")
BASH = os.environ.get("BASH") or (str(GIT_BASH) if GIT_BASH.exists() else shutil.which("bash"))
PWSH = shutil.which("pwsh")

FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


state = load()


def answer(command):
    if command.startswith("cmd package resolve-activity"):
        if state["home_fails"]:
            sys.exit(1)
        if not state["home"]:
            return ""
        return "priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n" + state["home"] + "\n"
    if command.startswith("cmd package query-activities"):
        if state["claims_fails"]:
            sys.exit(1)
        out = [f"{len(state['claims'])} activities found:"]
        for i, claim in enumerate(state["claims"]):
            out.append(f"  Activity #{i}:")
            out.append("    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true")
            out.append(f"    {claim}")
        return "\n".join(out) + "\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
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
    print(answer(args[-1]), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class HomeGuardTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.scripts = self.repo / "scripts"
        self.tools.mkdir(parents=True)
        self.scripts.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "disable.sh", self.tools / "disable.sh")
        shutil.copy2(ROOT / "scripts" / "tcl-pkg-validate.sh", self.scripts / "tcl-pkg-validate.sh")
        (self.tools / "tv.py").write_text(FAKE_TV, encoding="utf-8")

        # disable.sh calls tools/venv's Python. A tiny shell wrapper runs the real interpreter so
        # the fake tv.py is imported without needing the repo venv.
        wrapper = self.tools / "venv" / "bin" / "python"
        wrapper.parent.mkdir(parents=True)
        python = sys.executable.replace("\\", "/")
        wrapper.write_text(f'#!/bin/sh\nexec "{python}" "$@"\n', encoding="utf-8")
        wrapper.chmod(0o755)

        self.state_path = self.base / "state.json"
        self.list_path = self.base / "debloat.txt"
        self.write_list(["com.example.before", STOCK, "com.example.after"])

    def write_list(self, names):
        self.list_path.write_text("# test list\n" + "".join(f"{name}\n" for name in names), encoding="utf-8")

    def tv(self, **overrides):
        state = {
            "home": f"{STOCK}/.MainActivity",
            "claims": [f"{STOCK}/.MainActivity", FALLBACK],
            "home_fails": False,
            "claims_fails": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state), encoding="utf-8")

    def disable(self, extra_env=None):
        env = dict(os.environ, TV_HOST="192.0.2.1", FAKE_TV_STATE=str(self.state_path))
        env.update(extra_env or {})
        result = subprocess.run(
            [BASH, str(self.tools / "disable.sh").replace("\\", "/"),
             str(self.list_path).replace("\\", "/")],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=60)
        with open(self.state_path, encoding="utf-8") as f:
            commands = json.load(f)["commands"]
        return result, commands

    def output(self, result):
        return result.stdout + result.stderr

    @unittest.skipUnless(BASH, "bash is not available")
    def test_the_owner_of_home_is_skipped_when_it_is_the_only_real_home(self):
        self.tv()
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertEqual(sent[:2], [
            "pm disable-user --user 0 com.example.before",
            "pm disable-user --user 0 com.example.after",
        ])
        self.assertIn(f"Skipping {STOCK}", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_fallback_home_does_not_count_as_a_replacement(self):
        self.tv(claims=[FALLBACK])
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertIn(f"Skipping {STOCK}", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_a_second_real_home_lets_the_list_disable_the_owner(self):
        self.tv(claims=[f"{CUSTOM}/.MainActivity", f"{STOCK}/.MainActivity", FALLBACK])
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertNotIn("Skipping", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_force_disables_the_owner(self):
        self.tv()
        result, sent = self.disable({"FORCE_HOME": "1"})
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_guard_does_not_touch_a_list_without_the_owner(self):
        self.tv(home=f"{CUSTOM}/.MainActivity",
                claims=[f"{CUSTOM}/.MainActivity", f"{STOCK}/.MainActivity"])
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertNotIn("Skipping", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_a_tv_that_cannot_list_homes_keeps_the_owner(self):
        self.tv(claims_fails=True)
        result, sent = self.disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)


if __name__ == "__main__":
    unittest.main()
