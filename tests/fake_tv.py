"""A fake tools/tv.py for the debloat-script tests, and the harness that runs the real scripts on it.

Every test in this folder runs the real tools/disable.sh against a stand-in for tools/tv.py. The script
is not edited for the tests: it is copied into a temporary repository together with
scripts/tcl-pkg-validate.sh, and the client it calls is replaced by one that answers the queries a TV
would answer and records the batch. What reaches the fake is exactly what would have reached the TV, so
a guard that forgets a command, or adds one, shows up here.

What the TV answers lives in a JSON state file the test writes and the fake updates. That is how a test
says "this TV reports these TV_INPUT_HARDWARE packages" or "this read fails" without a second fake.

Run from the repository root:
    python -m unittest discover -s tests -v
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
GIT_BASH = Path(r"C:\Program Files\Git\bin\bash.exe")
BASH = os.environ.get("BASH") or (str(GIT_BASH) if GIT_BASH.exists() else shutil.which("bash"))
PWSH = os.environ.get("PWSH") or shutil.which("pwsh") or shutil.which("powershell")

# On MediaTek sets, and on the OnePlus TV of upstream #321, this is the package behind every HDMI, AV
# and tuner input.
TV_INPUT = "com.mediatek.tvinput"
STOCK = "com.google.android.tvlauncher"
CUSTOM = "br.eddy.tvhome"
FALLBACK = "com.android.tv.settings/.system.FallbackHome"

# The client the scripts call. It stands in for tools/tv.py wholesale, so it answers everything
# disable.sh asks: who owns Home, which HOME activities claim it, which listed packages ask for
# android.permission.TV_INPUT_HARDWARE, what ro.adb.secure says, and the --batch itself.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
state = json.load(open(STATE_PATH, encoding="utf-8"))


def save():
    with open(STATE_PATH, "w", encoding="utf-8") as f:
        json.dump(state, f)


def looped_packages(command):
    """The names in `for p in a b c; do ...`, the loop the input guard sends to the TV."""
    head = command.split(";", 1)[0]
    for prefix in ("for p in ", "for p in"):
        if head.startswith(prefix):
            return head[len(prefix):].split()
    return []


def answer(command):
    if command.startswith("cmd package resolve-activity"):
        if state["home_fails"]:
            sys.exit(1)
        if not state["home"]:
            return ""
        return ("priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true\n"
                + state["home"] + "\n")
    if command.startswith("cmd package query-activities"):
        if state["claims_fails"]:
            sys.exit(1)
        out = [f"{len(state['claims'])} activities found:"]
        for i, claim in enumerate(state["claims"]):
            out.append(f"  Activity #{i}:")
            out.append("    priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true")
            out.append(f"    {claim}")
        return "\n".join(out) + "\n"
    if "TV_INPUT_HARDWARE" in command:
        if state["input_fails"]:
            sys.exit(1)
        # On the TV this is `dumpsys package $p | grep -q TV_INPUT_HARDWARE && echo $p` per name.
        if state["input_raw"] is not None:
            # Whatever the TV chose to print, verbatim: the answer comes from the TV, so a test has to
            # be able to make it hostile, not only well-formed.
            return state["input_raw"]
        asked = looped_packages(command)
        return "".join(p + state["eol"] for p in asked if p in state["inputs"])
    if command.startswith("getprop"):
        if state["adb_fails"]:
            sys.exit(1)
        return state["adb_secure"] + state["eol"]
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
                # tools/tv.py echoes every command it runs; the tests read that echo to see in which
                # order the script said things. Flushed per line so the echo keeps its place next to
                # whatever the script wrote in between.
                sys.stdout.write(f"$ {line}\n")
                sys.stdout.flush()
        save()
        return 0
    sys.stdout.write(answer(args[-1]))
    sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class DebloatHarness(unittest.TestCase):
    """Copies the real scripts into a throwaway repository and points them at the fake client."""

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

    def write_list(self, names, header="# test list"):
        self.list_path.write_text(
            (header + "\n" if header else "") + "".join(f"{name}\n" for name in names), encoding="utf-8")

    def tv(self, **overrides):
        """What the TV answers. Anything not named here is a healthy, quiet, authorised TV."""
        state = {
            "home": f"{STOCK}/.MainActivity",
            "claims": [f"{STOCK}/.MainActivity", FALLBACK],
            "home_fails": False,
            "claims_fails": False,
            # The listed packages whose `dumpsys package` prints android.permission.TV_INPUT_HARDWARE.
            "inputs": [],
            # Set to a string to make the TV answer the lookup with exactly that, whatever the script
            # asked for; None keeps the well-formed answer built from "inputs".
            "input_raw": None,
            "input_fails": False,
            # 1 = the "Allow" prompt is required; 0 = any device on the network gets a shell.
            "adb_secure": "1",
            "adb_fails": False,
            # adb can end a line with CRLF; the scripts have to cope with either.
            "eol": "\n",
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state), encoding="utf-8")

    def run_disable(self, extra_env=None, timeout=60, merge_output=False):
        env = dict(os.environ, TV_HOST="192.0.2.1", FAKE_TV_STATE=str(self.state_path))
        env.update(extra_env or {})
        result = subprocess.run(
            [BASH, str(self.tools / "disable.sh").replace("\\", "/"),
             str(self.list_path).replace("\\", "/")],
            cwd=self.repo, env=env, text=True, timeout=timeout,
            # One stream keeps the order the script wrote things in: the warning goes to stderr and
            # the batch echo to stdout, and only a merged pipe can say which came first.
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT if merge_output else subprocess.PIPE)
        with open(self.state_path, encoding="utf-8") as f:
            commands = json.load(f)["commands"]
        return result, commands

    def output(self, result):
        return result.stdout + result.stderr

    @staticmethod
    def sent(commands, *packages):
        """True when none of these packages is in the batch the TV received."""
        return not any(f"pm disable-user --user 0 {p}" in commands for p in packages)