"""tools/disable.sh must say when the TV's network ADB accepts any device (ro.adb.secure=0, #319).

On firmware that reports 0, or that does not report the property at all, port 5555 hands a shell to
any device on the network with no "Allow" prompt. The run does not stop for it: it prints one warning
before the first command reaches the TV, and disables the list as asked.

Run from the repository root:
    python -m unittest tests.test_adb_exposure -v
"""
try:                                    # `discover -s tests` puts tests/ on the path
    from fake_tv import BASH, DebloatHarness
except ImportError:                     # `python -m unittest tests.test_adb_exposure` does not
    from tests.fake_tv import BASH, DebloatHarness
import unittest


LIST = ["com.example.one", "com.example.two"]
BATCH = ["pm disable-user --user 0 com.example.one",
         "pm disable-user --user 0 com.example.two",
         "pm list packages -d"]


class AdbSecureWarningTest(DebloatHarness):
    def setUp(self):
        super().setUp()
        self.write_list(LIST, header="# My TV")

    @unittest.skipUnless(BASH, "bash is not available")
    def test_warns_when_adb_is_not_secure(self):
        self.tv(adb_secure="0")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn("ro.adb.secure=0", self.output(result))
        # The note never changes what is sent.
        self.assertEqual(commands, BATCH)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_warns_when_the_property_is_not_set_at_all(self):
        # Empty is not a quiet "1": the firmware does not say the port needs an authorisation, so it
        # is treated the same as 0.
        self.tv(adb_secure="")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn("ro.adb.secure=<empty>", self.output(result))
        self.assertEqual(commands, BATCH)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_warns_with_crlf_output(self):
        self.tv(adb_secure="0", eol="\r\n")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertIn("ro.adb.secure=0", self.output(result))
        self.assertEqual(commands, BATCH)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_quiet_when_adb_is_secure(self):
        self.tv(adb_secure="1")
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn("ro.adb.secure", self.output(result))
        self.assertEqual(commands, BATCH)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_quiet_when_the_read_fails(self):
        # A failed read is not the same answer as an empty one: the scripts only skip the note and
        # never guess that the port is open.
        self.tv(adb_fails=True)
        result, commands = self.run_disable()
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn("ro.adb.secure", self.output(result))
        self.assertEqual(commands, BATCH)

    @unittest.skipUnless(BASH, "bash is not available")
    def test_the_warning_comes_before_the_first_command_reaches_the_tv(self):
        # The owner is told while the run can still be stopped, not after the TV has been changed.
        # The warning goes to stderr and the fake echoes each command to stdout, so the two are only
        # comparable on one merged stream.
        self.tv(adb_secure="0")
        result, commands = self.run_disable(merge_output=True)
        self.assertEqual(result.returncode, 0, result.stdout)
        text = result.stdout
        self.assertLess(text.index("ro.adb.secure=0"),
                        text.index("pm disable-user --user 0 com.example.one"))
        self.assertEqual(commands, BATCH)


if __name__ == "__main__":
    unittest.main()