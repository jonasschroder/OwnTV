"""The requested full device run must not silently omit existing instrumentation classes."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]


class TvSuiteInventoryTest(unittest.TestCase):
    def test_every_instrumentation_case_has_an_executing_ci_selector(self):
        cases = {}
        for path in (ROOT / 'app/src/androidTest/java').rglob('*.kt'):
            source = path.read_text()
            methods = re.findall(r'@Test\s+fun\s+(\w+)', source)
            if methods:
                package = re.search(r'^package (\S+)', source, re.M)[1]
                name = re.search(r'^class (\w+)', source, re.M)[1]
                cases[f'{package}.{name}'] = set(methods)
        suite = (ROOT / 'app/src/androidTest/java/tv/own/owntv/home/HomeRegressionSuite.kt').read_text()
        suite = suite.split('@Suite.SuiteClasses(', 1)[1].split(')', 1)[0]
        selected = {name if '.' in name else f'tv.own.owntv.home.{name}'
                    for name in re.findall(r'([\w.]+)::class', suite)}
        dedicated = {'tv.own.owntv.home.ProductionStartupTest',
                     'tv.own.owntv.update.SameSignerUpgradeTest',
                     'tv.own.owntv.update.UpdateInstallReceiverTest',
                     'tv.own.owntv.update.UpdateDialogNavigationTest'}
        performance = {'tv.own.owntv.performance.PerformanceJourneyTest'}
        self.assertEqual(set(cases), selected | dedicated | performance, 'Device tests must be selected by a real CI runner')
        workflow = (ROOT / '.github/workflows/android.yml').read_text()
        required = set(re.findall(r"\('([\w.]+)', '([\w]+)'\)", workflow))
        self.assertEqual({(name, method) for name in selected for method in cases[name]}, required,
                         'Every selected case must have a required executed/no-skip CI result')
        startup = (ROOT / 'tools/run-startup-regressions.sh').read_text()
        upgrades = (ROOT / 'tools/signing/test_upgrade.py').read_text()
        for name in dedicated:
            self.assertIn(name, startup if name.endswith('ProductionStartupTest') else upgrades)

        performance_script = (ROOT / 'tools/performance/run-journey.sh').read_text()
        performance_workflow = (ROOT / '.github/workflows/performance.yml').read_text()
        for name in performance:
            self.assertIn(name, performance_script)
            for method in cases[name]:
                self.assertIn('run ' + method + ' ', performance_script)
        self.assertIn('OK (1 test)', performance_script)
        self.assertIn('INSTRUMENTATION_STATUS_CODE: -3', performance_script)
        self.assertIn('bash tools/performance/run-journey.sh', performance_workflow)
        self.assertIn('mintv-synthetic-performance', performance_workflow)
