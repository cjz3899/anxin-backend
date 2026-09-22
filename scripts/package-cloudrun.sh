#!/usr/bin/env bash
# 生成微信云托管「本地上传」用的代码包：Dockerfile 与 app.jar 同级打成一个 zip。
# 云托管只负责跑镜像，构建在本地做，省掉云端 maven 拉依赖的时间。
# 用法：./scripts/package-cloudrun.sh [--skip-build]
set -euo pipefail

cd "$(dirname "$0")/.."

OUT_DIR="build/cloudrun"
SKIP_BUILD=0
if [[ "${1:-}" == "--skip-build" ]]; then
  SKIP_BUILD=1
fi

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  mvn -B -q -DskipTests package
fi

JAR="$(ls anxin-web/target/anxin-web-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "$JAR" ]]; then
  echo "找不到 anxin-web 的 jar，请去掉 --skip-build 先构建一次" >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp deploy/Dockerfile "$OUT_DIR/Dockerfile"
cp "$JAR" "$OUT_DIR/app.jar"

PACKAGE="$OUT_DIR/anxin-cloudrun.zip"
rm -f "$PACKAGE"
(cd "$OUT_DIR" && zip -q -r anxin-cloudrun.zip Dockerfile app.jar)

echo "jar    : $(du -h "$OUT_DIR/app.jar" | cut -f1)"
echo "代码包 : $(du -h "$PACKAGE" | cut -f1)  $PACKAGE"
echo "上传时在云托管「部署服务 → 代码创建 → 本地上传」里选这个 zip；端口填 8080。"
