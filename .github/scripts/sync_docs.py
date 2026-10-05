"""导出中文文档并同步到内容仓库的指定目录。"""

import argparse
import json
import posixpath
import re
from pathlib import Path, PurePosixPath
from urllib.parse import quote, unquote, urlsplit


TARGET_DIRECTORY = "亚托莉喵"
MANIFEST = ".atrimeow-docs.json"
INLINE_LINK = re.compile(
    r"(?P<code>`+)[^\n]*?(?P=code)"
    r"|(?P<prefix>!?\[[^\]\n]*\]\()"
    r"(?P<url><[^>\n]+>|[^\s()]+)"
    r"(?P<suffix>(?:\s+\"[^\"\n]*\"|\s+'[^'\n]*')?\))"
)
REFERENCE_LINK = re.compile(r"^( {0,3}\[[^\]\n]+\]:\s*)(<[^>\n]+>|\S+)(.*)$")


def checked_path(root: Path, relative: str) -> Path:
    parts = PurePosixPath(relative).parts
    if (not parts or PurePosixPath(relative).is_absolute()
            or any(part in (".", "..", ".git") for part in parts)
            or "\\" in relative or ":" in relative
            or PurePosixPath(relative).as_posix() != relative):
        raise ValueError(f"无效的相对路径: {relative}")
    path = root
    for part in parts:
        path = path / part
        if path.is_symlink() or path.resolve() != path.absolute():
            raise ValueError(f"路径不能经过链接: {relative}")
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"路径超出目录范围: {relative}")
    return path


def checked_document(root: Path, relative: str) -> Path:
    if len(PurePosixPath(relative).parts) > 2 or not relative.endswith(".md"):
        raise ValueError(f"文档只允许一层分类目录，且必须使用 .md 扩展名: {relative}")
    return checked_path(root, relative)


def read_mapping(source_root: Path) -> dict[str, str]:
    mapping = json.loads((source_root / ".github/docs-map.json").read_text(encoding="utf-8"))
    if not isinstance(mapping, dict) or not mapping:
        raise ValueError("文档映射不能为空")
    documents = {"README.md"} | {
        path.relative_to(source_root).as_posix()
        for path in (source_root / "docs").rglob("*.md")
    }
    if documents != set(mapping):
        raise ValueError(f"文档映射与源文件不一致: {sorted(documents ^ set(mapping))}")
    if not all(isinstance(name, str) for name in mapping.values()):
        raise ValueError("目标文件名必须为字符串")
    if len({name.casefold() for name in mapping.values()}) != len(mapping):
        raise ValueError("目标文件名重复")
    for source, destination in mapping.items():
        if not checked_path(source_root, source).is_file():
            raise ValueError(f"文档不存在: {source}")
        checked_document(source_root, destination)
    return mapping


def rewrite_url(url: str, source: str, destination: str, mapping: dict[str, str],
                source_root: Path, repository: str, revision: str, image: bool = False) -> str:
    angled = url.startswith("<") and url.endswith(">")
    value = url[1:-1] if angled else url
    parsed = urlsplit(value)
    if parsed.scheme or parsed.netloc or not parsed.path:
        return url
    path = unquote(parsed.path)
    source_path = posixpath.normpath(
        path.lstrip("/") if path.startswith("/") else posixpath.join(posixpath.dirname(source), path)
    )
    resolved = checked_path(source_root, source_path)
    if not resolved.exists():
        raise ValueError(f"{source} 存在无效链接: {url}")
    if source_path in mapping:
        result = quote(posixpath.relpath(mapping[source_path], posixpath.dirname(destination) or "."), safe="/")
    elif image:
        result = f"https://raw.githubusercontent.com/{repository}/{quote(revision, safe='')}/{quote(source_path, safe='/')}"
    else:
        kind = "tree" if resolved.is_dir() else "blob"
        result = f"https://github.com/{repository}/{kind}/{quote(revision, safe='')}/{quote(source_path, safe='/')}"
    if parsed.query:
        result += "?" + parsed.query
    if parsed.fragment:
        result += "#" + parsed.fragment
    return f"<{result}>" if angled else result


def render_document(text: str, source: str, destination: str, mapping: dict[str, str],
                    source_root: Path, repository: str, revision: str) -> str:
    def rewrite(url: str, image: bool = False) -> str:
        return rewrite_url(url, source, destination, mapping, source_root, repository, revision, image)

    def replace_link(match: re.Match) -> str:
        if match.group("code"):
            return match.group(0)
        prefix = match.group("prefix")
        return prefix + rewrite(match.group("url"), prefix.startswith("!")) + match.group("suffix")

    result = []
    fence = None
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if fence:
            result.append(line)
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence) and not marker[2].strip():
                fence = None
            continue
        if marker:
            fence = marker[1]
            result.append(line)
            continue
        if line.startswith(("    ", "\t")):
            result.append(line)
            continue
        reference = REFERENCE_LINK.match(line)
        if reference:
            start, end = reference.span(2)
            line = line[:start] + rewrite(reference[2]) + line[end:]
        else:
            line = INLINE_LINK.sub(replace_link, line)
        result.append(line)
    return "".join(result)


def sync(source_root: Path, target_root: Path, repository: str, revision: str) -> None:
    source_root = source_root.resolve()
    target_root = target_root.resolve()
    if source_root.is_relative_to(target_root) or target_root.is_relative_to(source_root):
        raise ValueError("源目录与目标仓库必须相互独立")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository) or not revision:
        raise ValueError("必须指定源仓库及版本")
    destination_root = checked_path(target_root, TARGET_DIRECTORY)
    manifest_path = checked_path(destination_root, MANIFEST)
    mapping = read_mapping(source_root)
    old_files = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else []
    if not isinstance(old_files, list) or not all(isinstance(name, str) for name in old_files):
        raise ValueError("已同步文档清单格式错误")
    destinations = {
        name: checked_document(destination_root, name)
        for name in set(old_files) | set(mapping.values())
    }
    for path in destinations.values():
        if path.exists() and not path.is_file():
            raise ValueError(f"文档目标不是文件: {path}")
    rendered = {
        destination: render_document(
            checked_path(source_root, source).read_text(encoding="utf-8"), source, destination,
            mapping, source_root, repository, revision,
        )
        for source, destination in mapping.items()
    }
    for name, content in rendered.items():
        path = destinations[name]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
    for name in sorted(set(old_files) - set(rendered)):
        destinations[name].unlink(missing_ok=True)
    manifest_path.write_text(json.dumps(sorted(rendered), ensure_ascii=False, indent=2) + "\n",
                             encoding="utf-8", newline="\n")
    print(f"已同步 {len(rendered)} 篇文档到 {TARGET_DIRECTORY}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--target-root", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()
    sync(args.source_root, args.target_root, args.repository, args.revision)


if __name__ == "__main__":
    main()
