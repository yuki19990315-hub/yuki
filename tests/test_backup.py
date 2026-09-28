import importlib.util, tempfile, unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location("backup",Path(__file__).parents[1]/"scripts"/"tokiha_backup.py")
backup=importlib.util.module_from_spec(spec);spec.loader.exec_module(backup)
class BackupHelpersTest(unittest.TestCase):
    def test_safe_name(self): self.assertEqual(backup.safe_name('a:b/c.jpg'),'a_b_c.jpg')
    def test_missing_config(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SystemExit): backup.sync(Path(directory)/"missing.json")
if __name__ == '__main__': unittest.main()
