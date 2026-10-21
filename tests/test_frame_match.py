"""scripts/frame-match.awk must agree with tools/bench.py about which frame answered a key.

The two measurement tools must not disagree: the shell launcher benchmark used to match the first
frame whose *intended vsync* came after the key, while `tools/bench.py` matches the first frame that
*finished* after it. On a launcher that moves the focus in a single frame the reacting frame is born
from the vsync before the key and only finishes after it, so the old rule dropped most keys and
mixed two methods in the history.

The rule now lives in one file, `scripts/frame-match.awk`, used by `tcl-launcher-bench.sh`, and this
test pins it against synthetic `framestats` dumps.

Run from the repository root:
    python -m unittest tests.test_frame_match -v
"""
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AWK = ROOT / "scripts" / "frame-match.awk"
GIT_BASH = Path(r"C:\Program Files\Git\bin\bash.exe")
BASH = str(GIT_BASH) if GIT_BASH.exists() else shutil.which("bash")


def frame(flags, intended_ms, completed_ms):
    """One PROFILEDATA row; only the columns the matcher reads are meaningful."""
    cols = ["0"] * 17
    cols[0] = str(flags)
    cols[1] = str(intended_ms * 1_000_000)
    cols[13] = str(completed_ms * 1_000_000)      # column 14, 1-based
    return ",".join(cols)


def dump(frames, uptime_ms=None):
    out = ["** Graphics info **", ""]
    if uptime_ms is not None:
        out.append(f"Uptime: {uptime_ms}")
    out += ["Total frames rendered: %d" % len(frames), "Janky frames: 0 (0.00%)", ""]
    # `dumpsys gfxinfo framestats` prints every row between two ---PROFILEDATA--- markers, not one
    # marker per row: a fixture with a marker per frame would hide every second row.
    out.append("---PROFILEDATA---")
    out.extend(frames)
    out.append("---PROFILEDATA---")
    return "\n".join(out) + "\n"


@unittest.skipUnless(BASH, "bash is not available")
class FrameMatchTest(unittest.TestCase):
    def setUp(self):
        # A directory per test: NamedTemporaryFile on Windows leaves a handle that makes the
        # next awk read an empty file, which made these tests pass alone and fail together.
        self._tmp = tempfile.TemporaryDirectory()
        self.base = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def run_awk(self, text, t0, t_before="", t_after="", key="22"):
        path = self.base / "framestats.txt"
        path.write_text(text, encoding="utf-8", newline="\n")
        args = [BASH, "-c",
                f'awk -v t0={t0} -v t_before={t_before} -v t_after={t_after} -v key={key} '
                f'-f "{AWK.as_posix()}" "{path.as_posix()}"']
        result = subprocess.run(args, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def test_frame_that_finishes_after_the_key_is_the_answer(self):
        # The frame is born before the key (intended 100 < t0 120) and finishes at 133.
        out = self.run_awk(dump([frame(0, 100, 133)]), t0=120)
        self.assertEqual(out, "13 13 22")

    def test_frame_finishing_before_the_key_is_ignored(self):
        out = self.run_awk(dump([frame(0, 90, 110), frame(0, 116, 133)]), t0=120)
        self.assertEqual(out, "13 13 22")

    def test_movement_stops_at_a_gap_over_50ms(self):
        frames = [frame(0, 116, 133), frame(0, 133, 150), frame(0, 300, 317)]
        out = self.run_awk(dump(frames), t0=120)
        # First frame answers at 133 (13 ms); the run ends at the last frame before the 167 ms gap.
        self.assertEqual(out, "13 30 22")

    def test_discarded_frames_are_skipped(self):
        out = self.run_awk(dump([frame(1, 116, 133), frame(0, 118, 140)]), t0=120)
        self.assertEqual(out, "20 20 22")

    def test_key_with_no_visual_reaction_is_dropped(self):
        # The first frame only finishes 400 ms later: it is not this key's answer.
        out = self.run_awk(dump([frame(0, 100, 520)]), t0=120)
        self.assertEqual(out, "")

    def test_key_outside_the_reset_window_uses_the_window_start(self):
        # t0 (50) is before the reset (100): the log line belongs to another key.
        out = self.run_awk(dump([frame(0, 99, 113)]), t0=50, t_before=100, t_after=200)
        self.assertEqual(out, "13 13 22")


if __name__ == "__main__":
    unittest.main()
