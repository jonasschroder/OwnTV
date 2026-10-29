"""Include standalone lab regressions in the existing tools CI discovery."""
import importlib.util
from pathlib import Path


def load_tests(loader, tests, pattern):
    path = Path(__file__).resolve().parents[1] / 'broadcast-lab/tests/test_broadcast_lab.py'
    spec = importlib.util.spec_from_file_location('broadcast_lab_phase2_tests', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return loader.loadTestsFromModule(module)
