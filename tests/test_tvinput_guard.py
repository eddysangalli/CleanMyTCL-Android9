"""tools/disable.sh must keep the packages that drive the HDMI/AV/tuner inputs (#321).

On a OnePlus TV (Android 11, MediaTek/MStar) disabling them left every HDMI port detecting the cable
and showing "No Signal", and only a full restore plus a cold boot brought the picture back.

The real `disable.sh` runs against the fake client in `tests/fake_tv.py`: the fake answers the
TV_INPUT_HARDWARE lookup with the packages the test says drive the inputs, tells every other command
apart (the batch is recorded, the Home guard is answered the way a working TV would) and the test then
looks at the batch that would have reached the TV.

Run from the repository root:
    python -m unittest tests.test_tvinput_guard -v
"""
try:                                    # `discover -s tests` puts tests/ on the path
    from fake_tv import BASH, STOCK, TV_INPUT, DebloatHarness
except ImportError:                     # `python -m unittest tests.test_tvinput_guard` does not
    from tests.fake_tv import BASH, STOCK, TV_INPUT, DebloatHarness
import unittest


LIST = ["com.example.before", TV_INPUT, "com.example.after"]
BATCH = ["pm disable-user --user 0 com.example.before",
         f"pm disable-user --user 0 {TV_INPUT}",
         "pm disable-user --user 0 com.example.after",
         "pm list packages -d"]
WITHOUT_INPUT = ["pm disable-user --user 0 com.example.before",
                 "pm disable-user --user 0 com.example.after",
                 "pm list packages -d"]


class TvInputGuardTest(DebloatHarness):
    def setUp(self):
        super().setUp()
        self.write_list(LIST, header="# My TV")

    @unittest.skipUnless(BASH, "bash is not available")
    def test_input_package_is_skipped(self):
        self.tv(inputs=[TV_INPUT])
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, WITHOUT_INPUT)
        self.assertIn(f"Skipping {TV_INPUT}", self.output(result))
        self.assertIn("TV_INPUT_HARDWARE", self.output(result))
        self.assertIn("TVINPUT_ALLOW=1", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_crlf_from_adb_still_matches(self):
        # adb can end a line with CRLF; a stray CR would leave the name unmatched and the port broken.
        self.tv(inputs=[TV_INPUT], eol="\r\n")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {TV_INPUT}", commands)
        self.assertIn(f"Skipping {TV_INPUT}", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_disabled_when_the_tv_reports_no_hardware_inputs(self):
        self.tv(inputs=[])
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, BATCH)
        self.assertNotIn("Skipping", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_force(self):
        self.tv(inputs=[TV_INPUT])
        result, commands = self.run_disable({"TVINPUT_ALLOW": "1"})
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, BATCH)
        self.assertNotIn("Skipping", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_failed_check_warns_and_keeps_the_list(self):
        # tv.py failed (no connection, a timeout): do not skip on a guess, but say the check did not
        # happen, because a black input is easy to blame on the cable.
        self.tv(input_fails=True)
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, BATCH)
        self.assertIn("Could not check", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_only_names_from_the_list_are_matched(self):
        # A package the TV reports that is not in this list must not lose a line or add one.
        self.tv(inputs=[TV_INPUT, "com.android.tv"])
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, WITHOUT_INPUT)
        self.assertNotIn("com.android.tv", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_a_name_from_the_tv_cannot_add_a_command(self):
        # The lookup output comes from the TV, so it is only ever compared against the list, never
        # pasted back into a command. The fake here answers with a name carrying shell syntax.
        self.tv(input_raw="com.example.before; reboot\n")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, BATCH)
        self.assertNotIn("reboot", "\n".join(commands))
        self.assertNotIn("Skipping", self.output(result))

    @unittest.skipUnless(BASH, "bash is not available")
    def test_the_home_guard_and_the_input_guard_both_run(self):
        # Neither guard may swallow the other: a list with the owner of Home and an input driver in it
        # loses exactly those two lines, and both say so.
        self.write_list([STOCK, TV_INPUT, "com.example.after"])
        self.tv(inputs=[TV_INPUT], claims=[f"{STOCK}/.MainActivity"])
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(commands, ["pm disable-user --user 0 com.example.after",
                                    "pm list packages -d"])
        self.assertIn(f"Skipping {STOCK}", self.output(result))
        self.assertIn(f"Skipping {TV_INPUT}", self.output(result))


if __name__ == "__main__":
    unittest.main()