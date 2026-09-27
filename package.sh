#!/bin/bash
set -euo pipefail

# 定义颜色代码
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

mvn -B package \
    -DskipTests \
    -P windows,macos \
    --file pom.xml

echo -e "${GREEN}jar编译完成${NC}"
