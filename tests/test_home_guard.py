"""Home guard for the debloat scripts (#239/#350).

The scripts must not disable the app that owns Home while no other real HOME activity remains
enabled. `tools/tv.py` is replaced by a fake that answers the two package-manager queries; the real
`disable.sh` still runs, validates the list and builds the batch. The fake and the harness live in
`tests/fake_tv.py`, shared with the input guard (`tests/test_tvinput_guard.py`) and the network-ADB
warning (`tests/test_adb_exposure.py`).

Run from the repository root:
    python -m unittest tests.test_home_guard -v
"""
try:                                    # `discover -s tests` puts tests/ on the path
    from fake_tv import BASH, CUSTOM, FALLBACK, STOCK, DebloatHarness
except ImportError:                     # `python -m unittest tests.test_home_guard` does not
    from tests.fake_tv import BASH, CUSTOM, FALLBACK, STOCK, DebloatHarness
import unittest


class HomeGuardTest(DebloatHarness):
    def setUp(self):
        super().setUp()
        self.write_list(["com.example.before", STOCK, "com.example.after"])

    def disable(self, extra_env=None):
        result, commands = self.run_disable(extra_env)
        return result, commands

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