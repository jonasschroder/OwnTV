import copy
import importlib.util
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("package_visibility", Path(__file__).parents[1] / "mintv_package_visibility.py")
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class PackageVisibilityTest(unittest.TestCase):
    def manifest(self):
        return ET.parse(Path(__file__).parents[2] / "app/src/main/AndroidManifest.xml").getroot()

    def test_declared_known_clients_and_existing_shortcuts_pass(self):
        policy.verify_visibility(self.manifest())

    def test_broad_unknown_or_provider_visibility_is_rejected(self):
        for tag, attrs in [
            ("uses-permission", {policy.ANDROID + "name": "android.permission.QUERY_ALL_PACKAGES"}),
            ("uses-permission-sdk-23", {policy.ANDROID + "name": "android.permission.QUERY_ALL_PACKAGES"}),
            ("queries/package", {policy.ANDROID + "name": "unreviewed.example"}),
            ("queries/provider", {policy.ANDROID + "authorities": "unreviewed.example"}),
        ]:
            root = self.manifest()
            parent, _, child = tag.rpartition("/")
            ET.SubElement(root.find(parent) if parent else root, child or tag, attrs)
            with self.subTest(tag=tag), self.assertRaises(AssertionError):
                policy.verify_visibility(root)

    def test_missing_clients_or_documented_uri_queries_are_rejected(self):
        original = self.manifest()
        targets = [n for n in original.findall("queries/package")
                   if n.get(policy.ANDROID + "name") in {"com.s0und.s0undtv", "com.fgl27.twitch", "tv.twitch.android.app", "se.svt.android.svtplay", "se.telenor.stream"}]
        targets += original.findall("queries/intent")
        for target in targets:
            root = copy.deepcopy(original)
            query = root.find("queries")
            for node in list(query):
                if ET.tostring(node) == ET.tostring(target):
                    query.remove(node)
            with self.subTest(node=ET.tostring(target)), self.assertRaises(AssertionError):
                policy.verify_visibility(root)
