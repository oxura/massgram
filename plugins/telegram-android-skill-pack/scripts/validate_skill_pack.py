#!/usr/bin/env python3
"""Validate the Telegram Android skill pack structure."""

from __future__ import annotations

from pathlib import Path


EXPECTED_SKILLS = [
    "telegram-orchestrator",
    "telegram-design-principles",
    "telegram-ui-implementation",
    "telegram-component-reuse",
    "telegram-feature-development",
    "telegram-legacy-modification",
    "telegram-product-consistency",
    "telegram-code-style-and-architecture",
    "telegram-review-and-regression",
]

EXPECTED_REFERENCES = [
    "design-principles.md",
    "ui-anti-patterns.md",
    "feature-development-playbook.md",
    "legacy-edit-checklist.md",
    "product-consistency-rules.md",
    "code-review-rules.md",
    "regression-review-checklist.md",
]


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def validate_skill(skill_dir: Path, errors: list[str]) -> None:
    skill_md = skill_dir / "SKILL.md"
    openai_yaml = skill_dir / "agents" / "openai.yaml"

    if not skill_md.exists():
        errors.append(f"missing {skill_md}")
        return
    if not openai_yaml.exists():
        errors.append(f"missing {openai_yaml}")

    content = read_text(skill_md)
    if "[TODO:" in content:
        errors.append(f"unfinished TODO in {skill_md}")
    if not content.startswith("---\nname: "):
        errors.append(f"invalid frontmatter start in {skill_md}")
        return

    lines = content.splitlines()
    if len(lines) < 4 or lines[3] != "---":
        errors.append(f"incomplete frontmatter in {skill_md}")
        return

    expected_name = f"name: {skill_dir.name}"
    if lines[1].strip() != expected_name:
        errors.append(f"name mismatch in {skill_md}: expected '{expected_name}'")
    if not lines[2].startswith("description: ") or lines[2] == "description: ":
        errors.append(f"missing description in {skill_md}")


def main() -> int:
    plugin_root = Path(__file__).resolve().parent.parent
    errors: list[str] = []

    plugin_manifest = plugin_root / ".codex-plugin" / "plugin.json"
    marketplace_manifest = plugin_root.parent.parent / ".agents" / "plugins" / "marketplace.json"

    if not plugin_manifest.exists():
        errors.append(f"missing {plugin_manifest}")
    if not marketplace_manifest.exists():
        errors.append(f"missing {marketplace_manifest}")

    references_dir = plugin_root / "references"
    for ref_name in EXPECTED_REFERENCES:
        ref_path = references_dir / ref_name
        if not ref_path.exists():
            errors.append(f"missing {ref_path}")
        elif "[TODO:" in read_text(ref_path):
            errors.append(f"unfinished TODO in {ref_path}")

    skills_dir = plugin_root / "skills"
    for skill_name in EXPECTED_SKILLS:
        skill_dir = skills_dir / skill_name
        if not skill_dir.exists():
            errors.append(f"missing {skill_dir}")
            continue
        validate_skill(skill_dir, errors)

    if errors:
        print("Validation failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print("Skill pack validation passed.")
    print(f"Plugin root: {plugin_root}")
    print(f"Skills checked: {len(EXPECTED_SKILLS)}")
    print(f"References checked: {len(EXPECTED_REFERENCES)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
