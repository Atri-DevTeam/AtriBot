import json
import re
import tempfile
import unittest
from pathlib import Path
from urllib.parse import unquote, urlsplit

from sync_docs import INLINE_LINK, MANIFEST, TARGET_DIRECTORY, read_mapping, sync


class DocumentationSyncTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name).resolve()
        self.source = root / "source"
        self.target = root / "website"
        (self.source / ".github").mkdir(parents=True)
        (self.source / "docs/framework").mkdir(parents=True)
        self.target.mkdir()
        self.output = self.target / TARGET_DIRECTORY
        self.mapping = {
            "README.md": "介绍.md",
            "docs/README.md": "文档目录.md",
            "docs/framework/events.md": "框架/事件.md",
        }
        self.write_mapping()
        (self.source / "README.md").write_text(
            '# Project\n[目录](docs/README.md)\n[事件](docs/framework/events.md#注册)\n'
            '[源码](example.java#L1)\n[目录源码](docs/framework)\n'
            '[外部](https://example.com/a?q=1#test)\n'
            '`[示例](missing.md)`\n```md\n[示例](missing.md)\n```\n'
            '[引用][events]\n[events]: docs/framework/events.md "事件"\n',
            encoding="utf-8",
        )
        (self.source / "example.java").write_text("class Example {}\n", encoding="utf-8")
        (self.source / "docs/README.md").write_text('[首页](../README.md)\n', encoding="utf-8")
        (self.source / "docs/framework/events.md").write_text(
            '# 事件\n[目录](../README.md)\n[注册](#注册)\n## 注册\n', encoding="utf-8")

    def write_mapping(self):
        (self.source / ".github/docs-map.json").write_text(
            json.dumps(self.mapping, ensure_ascii=False), encoding="utf-8")

    def run_sync(self):
        sync(self.source, self.target, "example/project", "abc123")

    def test_links_code_blocks_and_repeat_export(self):
        self.run_sync()
        intro = (self.output / "介绍.md").read_text(encoding="utf-8")
        self.assertIn('[目录](%E6%96%87%E6%A1%A3%E7%9B%AE%E5%BD%95.md)', intro)
        self.assertIn('https://github.com/example/project/blob/abc123/example.java#L1', intro)
        self.assertIn('https://github.com/example/project/tree/abc123/docs/framework', intro)
        self.assertIn('[外部](https://example.com/a?q=1#test)', intro)
        self.assertIn('`[示例](missing.md)`\n```md\n[示例](missing.md)\n```', intro)
        self.assertIn('[events]: %E6%A1%86%E6%9E%B6/%E4%BA%8B%E4%BB%B6.md "事件"', intro)
        event = (self.output / "框架/事件.md").read_text(encoding="utf-8")
        self.assertIn('[目录](../%E6%96%87%E6%A1%A3%E7%9B%AE%E5%BD%95.md)', event)
        self.assertIn('[注册](#注册)', event)
        before = {path.relative_to(self.output): path.read_bytes() for path in self.output.rglob('*') if path.is_file()}
        self.run_sync()
        after = {path.relative_to(self.output): path.read_bytes() for path in self.output.rglob('*') if path.is_file()}
        self.assertEqual(before, after)

    def test_rename_only_removes_previously_managed_document(self):
        self.run_sync()
        (self.target / "其他项目.md").write_text("outside", encoding="utf-8")
        (self.output / "手工文档.md").write_text("manual", encoding="utf-8")
        self.mapping['docs/framework/events.md'] = '框架/事件参考.md'
        self.write_mapping()
        self.run_sync()
        self.assertFalse((self.output / '框架/事件.md').exists())
        self.assertTrue((self.output / '框架/事件参考.md').exists())
        self.assertEqual('outside', (self.target / '其他项目.md').read_text(encoding='utf-8'))
        self.assertEqual('manual', (self.output / '手工文档.md').read_text(encoding='utf-8'))

    def test_removed_source_cleans_only_managed_file(self):
        self.run_sync()
        del self.mapping['docs/framework/events.md']
        self.write_mapping()
        (self.source / 'docs/framework/events.md').unlink()
        (self.source / 'README.md').write_text('# Project\n', encoding='utf-8')
        self.run_sync()
        self.assertFalse((self.output / '框架/事件.md').exists())

    def test_missing_mapping_fails_before_writing(self):
        (self.source / 'docs/new.md').write_text('# New\n', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '映射与源文件不一致'):
            self.run_sync()
        self.assertFalse(self.output.exists())

    def test_invalid_mapping_paths_and_duplicates(self):
        for destination in ('../outside.md', '一级/二级/文档.md', '/outside.md', 'C:/outside.md', '文档目录.md'):
            with self.subTest(destination=destination):
                self.mapping['README.md'] = destination
                self.write_mapping()
                with self.assertRaises(ValueError):
                    self.run_sync()
                self.assertFalse(self.output.exists())

    def test_manifest_cannot_delete_outside_directory(self):
        self.output.mkdir()
        outside = self.target / '外部.md'
        outside.write_text('retain', encoding='utf-8')
        (self.output / MANIFEST).write_text('["../外部.md"]', encoding='utf-8')
        with self.assertRaises(ValueError):
            self.run_sync()
        self.assertEqual('retain', outside.read_text(encoding='utf-8'))
        self.assertFalse((self.output / '介绍.md').exists())

    def test_broken_link_fails_before_overwriting_documents(self):
        self.run_sync()
        before = (self.output / '介绍.md').read_bytes()
        (self.source / 'docs/framework/events.md').write_text('[失效](missing.md)', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, '无效链接'):
            self.run_sync()
        self.assertEqual(before, (self.output / '介绍.md').read_bytes())

    def test_destination_symlink_is_rejected(self):
        external = self.target / '其他目录'
        external.mkdir()
        try:
            self.output.symlink_to(external, target_is_directory=True)
        except OSError:
            self.skipTest('当前环境不允许创建符号链接')
        with self.assertRaisesRegex(ValueError, '链接'):
            self.run_sync()
        self.assertEqual([], list(external.iterdir()))

    def test_project_documents_export_with_resolvable_local_links(self):
        project = Path(__file__).resolve().parents[2]
        sync(project, self.target, 'Atri-DevTeam/AtriBot', 'abc123')
        mapping = read_mapping(project)
        exported = list(self.output.rglob('*.md'))
        self.assertEqual(len(mapping), len(exported))
        for path in exported:
            self.assertLessEqual(len(path.relative_to(self.output).parts), 2)
            text = path.read_text(encoding='utf-8')
            for match in INLINE_LINK.finditer(text):
                if match.group('code'):
                    continue
                parsed = urlsplit(match.group('url').strip('<>'))
                if parsed.scheme or parsed.netloc:
                    continue
                target = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
                self.assertTrue(target.is_relative_to(self.output), (path, parsed.path))
                self.assertTrue(target.is_file(), (path, parsed.path))
                if parsed.fragment:
                    headings = re.findall(r'^#{1,6} (.+)$', target.read_text(encoding='utf-8'), re.M)
                    anchors = {re.sub(r'[^\w\- ]', '', heading.lower()).replace(' ', '-') for heading in headings}
                    self.assertIn(unquote(parsed.fragment), anchors, (path, parsed.fragment))


if __name__ == '__main__':
    unittest.main()
