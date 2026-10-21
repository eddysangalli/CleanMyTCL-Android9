"""scripts/tcl-lib.sh must put the accessibility services back after a force-stop.

On this TCL `am force-stop <launcher>` removes that package's services from
`enabled_accessibility_services` and they do not come back when the Home opens again (upstream PR
#503 reported it on a 55C715; reproduced on the BeyondTV2 V660 on 2026-10-20). Every cold-start
benchmark force-stops the launcher, so without a save/restore each run silently killed the remote's
button remapping.

Three things this locks down, each of which was wrong at some point:

- the list must be **appended to**, never replaced: writing one service on its own wipes ours and
  any other app's;
- `pkg/pkg.Classe` and `pkg/.Classe` are the same component, so the restore must not add the same
  service twice (the TV rewrites it in the short form);
- services that appeared while the test was running must survive.

The real functions run against a fake `adb` that keeps the setting in a file and removes a
package's services on `am force-stop`, the way the TV does.

Run from the repository root:
    python -m unittest tests.test_accessibility_guard -v
"""
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GIT_BASH = Path(r"C:\Program Files\Git\bin\bash.exe")
BASH = str(GIT_BASH) if GIT_BASH.exists() else shutil.which("bash")

OURS = "br.eddy.tvhome/com.gathin.tvhome.KeyRemapService"
OTHER = "com.exemplo.outroapp/com.exemplo.outroapp.Servico"

# A fake adb: `-s <serial> shell settings get|put secure <key> [value]`, plus `am force-stop <pkg>`
# removing that package's entries, which is what the TV does.
FAKE_ADB = r"""#!/usr/bin/env bash
# args: -s <serial> shell <rest...>
shift 2
[ "$1" = shell ] || exit 0
shift
case "$1" in
  settings)
    action="$2"; scope="$3"; key="$4"; value="$5"
    case "$action" in
      get) cat "$FAKE_ACC_STATE" ;;
      put)
        case "$key" in
          enabled_accessibility_services) printf '%s' "$value" > "$FAKE_ACC_STATE" ;;
          accessibility_enabled) : ;;
        esac ;;
    esac ;;
  am)
    [ "$2" = force-stop ] || exit 0
    pkg="$3"
    cur=$(cat "$FAKE_ACC_STATE")
    kept=""
    old_ifs="$IFS"; IFS=':'
    for part in $cur; do
      [ -n "$part" ] || continue
      case "$part" in
        "$pkg"/*) ;;                       # the TV drops the stopped package's services
        *) kept="${kept:+$kept:}$part" ;;
      esac
    done
    IFS="$old_ifs"
    printf '%s' "$kept" > "$FAKE_ACC_STATE" ;;
esac
"""

# Sources the library and exercises save + (simulated) force-stop + restore.
DRIVER = r"""
. scripts/tcl-lib.sh
tv_acc_save
tv am force-stop br.eddy.tvhome
tv_acc_restore
cat "$FAKE_ACC_STATE"
"""


@unittest.skipUnless(BASH, "bash is not available")
class AccessibilityGuardTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = Path(self.tmp.name)
        self.state = self.base / "acc.txt"
        bin_dir = self.base / "bin"
        bin_dir.mkdir()
        adb = bin_dir / "adb"
        adb.write_text(FAKE_ADB, encoding="utf-8")
        adb.chmod(0o755)
        self.env = dict(os.environ)
        self.env.update({
            "ADB": str(adb),
            "TCL_TV_HOST": "10.0.0.1",
            "FAKE_ACC_STATE": str(self.state),
            "MSYS_NO_PATHCONV": "1",
        })

    def tearDown(self):
        self.tmp.cleanup()

    def run_case(self, initial):
        self.state.write_text(initial, encoding="utf-8")
        result = subprocess.run(
            [BASH, "-c", DRIVER],
            cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        return self.state.read_text(encoding="utf-8")

    def test_our_service_is_restored(self):
        self.assertEqual(self.run_case(OURS), OURS)

    def test_another_apps_service_is_kept(self):
        self.assertEqual(self.run_case(f"{OTHER}:{OURS}"), f"{OTHER}:{OURS}")

    def test_short_and_long_component_forms_do_not_duplicate(self):
        """The TV rewrites `pkg/pkg.Classe` as `pkg/.Classe`; restoring must not add it twice."""
        out = self.run_case("com.exemplo.outroapp/.Servico:" + OURS)
        self.assertEqual(out.count("com.exemplo.outroapp/"), 1, out)
        self.assertIn(OURS, out)

    def test_empty_list_stays_empty(self):
        """Nothing was enabled before the run, so the restore must not invent a service."""
        self.assertEqual(self.run_case(""), "")

    def test_service_that_appeared_during_the_run_survives(self):
        """A service enabled while the benchmark was running is not ours to drop."""
        self.state.write_text(f"{OTHER}:{OURS}", encoding="utf-8")
        # Simulate: the force-stop drops ours, another app's service shows up meanwhile.
        driver = r"""
. scripts/tcl-lib.sh
tv_acc_save
printf '%s' "com.outro.novo/.Svc" > "$FAKE_ACC_STATE"
tv_acc_restore
cat "$FAKE_ACC_STATE"
"""
        result = subprocess.run(
            [BASH, "-c", driver],
            cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        out = self.state.read_text(encoding="utf-8")
        self.assertIn("com.outro.novo/.Svc", out)
        self.assertIn(OURS, out)

    def test_recover_after_a_killed_run(self):
        """SIGKILL cannot be trapped; the on-disk copy is what puts the service back.

        A run killed by SIGKILL, or a terminal closed mid-run, leaves the setting empty and the
        backup on disk. The next script calls tv_acc_recover and the state comes back.
        """
        backup = self.base / "acc-backup"
        backup.write_text(OURS, encoding="utf-8")
        self.state.write_text("", encoding="utf-8")
        self.env["ACC_BACKUP"] = str(backup)
        driver = r"""
. scripts/tcl-lib.sh
tv_acc_recover
cat "$FAKE_ACC_STATE"
"""
        result = subprocess.run(
            [BASH, "-c", driver],
            cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state.read_text(encoding="utf-8"), OURS)
        self.assertFalse(backup.exists(), "the backup is consumed once it has been applied")

    def test_recover_does_not_touch_a_list_that_is_not_empty(self):
        """If nothing was lost, the backup is stale and must not overwrite the current list."""
        backup = self.base / "acc-backup"
        backup.write_text(OURS, encoding="utf-8")
        self.state.write_text(OTHER, encoding="utf-8")
        self.env["ACC_BACKUP"] = str(backup)
        driver = r"""
. scripts/tcl-lib.sh
tv_acc_recover
cat "$FAKE_ACC_STATE"
"""
        result = subprocess.run(
            [BASH, "-c", driver],
            cwd=ROOT, env=self.env, capture_output=True, text=True, timeout=60)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.state.read_text(encoding="utf-8"), OTHER)


if __name__ == "__main__":
    unittest.main()
