#!/bin/bash
# ============================================================
#  AI Review Assistant - 一键管理脚本
#  用法: ./run.sh {start|stop|restart|status|build|test|logs|errors|doctor|help}
#
#  功能:
#    start    一键启动 (环境检测 → PostgreSQL → 构建检查 → 应用)
#    stop     停止应用
#    restart  重启应用
#    status   检测并输出当前所有服务状态
#    build    构建前端 + 后端
#    test     运行后端测试
#    logs     实时查看应用日志
#    errors   输出最近的应用错误
#    doctor   全面体检 (环境/依赖/端口/配置)
#    help     显示帮助
# ============================================================

set -euo pipefail

# ============================================================
# 配置区 (按需修改)
# ============================================================
APP_NAME="ai-review-assistant"
APP_PORT=8080
APP_MAIN_CLASS="com.aiservice.aireviewassistant.AiReviewAssistantApplication"
PID_FILE=".app.pid"
LOG_FILE="app.log"
JAR_FILE="target/${APP_NAME}-0.0.1-SNAPSHOT.jar"
FRONTEND_DIR="frontend"
HEALTH_URL="http://localhost:${APP_PORT}/actuator/health"
START_TIMEOUT=90          # 启动等待秒数
STOP_TIMEOUT=10           # 优雅停止等待秒数
PG_CONTAINER="ai-review-postgres"

# ============================================================
# 工具函数: 颜色与输出
# ============================================================
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'
NC='\033[0m'

info()  { echo -e "  ${BLUE}ℹ${NC}  $*"; }
ok()    { echo -e "  ${GREEN}✔${NC}  $*"; }
warn()  { echo -e "  ${YELLOW}⚠${NC}  $*"; }
err()   { echo -e "  ${RED}✖${NC}  $*"; }
line()  { echo -e "${DIM}$(printf '%.0s─' {1..50})${NC}"; }

# 旋转等待动画: spinner <消息> <PID>
spinner() {
    local msg="$1" pid="$2" delay=0.15
    local chars='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0
    echo -ne "  ${CYAN}${chars:i++:1}${NC}  ${msg}\033[0K\r"
    while kill -0 "$pid" 2>/dev/null; do
        echo -ne "  ${CYAN}${chars:i++:1}${NC}  ${msg}\033[0K\r"
        i=$(( (i + 1) % ${#chars} ))
        sleep "$delay"
    done
    echo -ne "\033[0K"
}

# ============================================================
# 环境检测
# ============================================================

# 检测 Java 17+
check_java() {
    if ! command -v java &>/dev/null; then
        err "未检测到 Java，请安装 JDK 17+"
        return 1
    fi
    # 提取版本号: openjdk version "22.0.1" → 22 (用 ^[^"]* 锚定第一个引号，避免贪婪匹配)
    local ver
    ver=$(java -version 2>&1 | head -1 | sed 's/^[^"]*"\([0-9]*\).*/\1/')
    if [ "${ver:-0}" -lt 17 ] 2>/dev/null; then
        err "需要 JDK 17+，当前版本: $ver"
        return 1
    fi
    ok "Java $(java -version 2>&1 | head -1 | sed 's/^[^"]*"\(.*\)".*/\1/')"
    return 0
}

# 检测 Node.js (前端构建需要)
check_node() {
    if ! command -v node &>/dev/null; then
        warn "未检测到 Node.js，前端构建将跳过 (仅后端运行)"
        return 1
    fi
    ok "Node.js $(node -v 2>/dev/null)"
    return 0
}

# 检测 Docker
check_docker() {
    if ! command -v docker &>/dev/null; then
        warn "未检测到 Docker，PostgreSQL 将无法自动启动"
        return 1
    fi
    if ! docker info &>/dev/null 2>&1; then
        warn "Docker 未运行，请启动 Docker Desktop"
        return 1
    fi
    return 0
}

# 检测 PostgreSQL 容器是否运行且就绪
check_postgres() {
    docker ps 2>/dev/null | grep -q "$PG_CONTAINER" || return 1
    docker exec "$PG_CONTAINER" pg_isready -U postgres &>/dev/null 2>&1
}

# 检测本地 PostgreSQL 是否占用 5432 端口 (与 Docker 冲突)
# 注意: 只检测"监听"5432 的进程，应用作为 PG 客户端连接不算冲突
check_local_postgres() {
    local local_pg_pid
    local_pg_pid=$(lsof -ti:5432 -sTCP:LISTEN -P -n 2>/dev/null | head -1)
    [ -z "$local_pg_pid" ] && return 0
    local proc_cmd
    proc_cmd=$(ps -p "$local_pg_pid" -o command= 2>/dev/null)
    if echo "$proc_cmd" | grep -qi "postgres" && ! echo "$proc_cmd" | grep -q "docker"; then
        warn "本地 PostgreSQL 占用 5432 (PID: $local_pg_pid)"
        warn "请先停止: brew services stop postgresql@16 或 pg_ctl stop"
        return 1
    fi
    return 0
}

# 检测 DashScope API Key 是否配置
check_api_key() {
    if [ -z "${DASHSCOPE_API_KEY:-}" ]; then
        warn "未配置 DASHSCOPE_API_KEY，AI 功能将不可用"
        warn "设置: export DASHSCOPE_API_KEY=sk-xxxx"
        return 1
    fi
    ok "DASHSCOPE_API_KEY 已配置"
    return 0
}

# ============================================================
# PostgreSQL 管理
# ============================================================

ensure_postgres() {
    if check_postgres; then
        ok "PostgreSQL + pgvector (端口 5432)"
        return 0
    fi

    if ! check_docker; then
        err "需要 Docker Desktop 来运行 PostgreSQL"
        return 1
    fi

    check_local_postgres || true

    info "启动 PostgreSQL + pgvector..."
    docker-compose up -d postgres >/dev/null 2>&1 || {
        err "docker-compose 启动失败，运行: docker-compose logs postgres"
        return 1
    }

    local retries=0
    while ! check_postgres; do
        retries=$((retries + 1))
        if [ $retries -ge 30 ]; then
            err "PostgreSQL 启动超时！运行: docker logs $PG_CONTAINER"
            return 1
        fi
        sleep 1
    done
    ok "PostgreSQL + pgvector (端口 5432)"
    return 0
}

stop_postgres() {
    if docker ps 2>/dev/null | grep -q "$PG_CONTAINER"; then
        info "停止 PostgreSQL..."
        docker-compose stop postgres >/dev/null 2>&1
        ok "PostgreSQL 已停止"
    fi
}

# ============================================================
# 应用进程管理
# ============================================================

# 判断 PID 是否为本应用的 Java 进程
_is_app_pid() {
    local pid="$1"
    [ -z "$pid" ] && return 1
    kill -0 "$pid" 2>/dev/null || return 1
    local cmd
    cmd=$(ps -p "$pid" -o command= 2>/dev/null)
    echo "$cmd" | grep -qE "^[^ ]*java " && \
        (echo "$cmd" | grep -q "$APP_MAIN_CLASS" || echo "$cmd" | grep -q "$JAR_FILE")
}

# 获取监听 APP_PORT 的进程 PID
_get_listen_pid() {
    lsof -ti:"$APP_PORT" -sTCP:LISTEN -P -n 2>/dev/null | head -1
}

# 应用是否运行中
is_running() {
    local pid=""
    [ -f "$PID_FILE" ] && pid=$(cat "$PID_FILE" 2>/dev/null)
    if _is_app_pid "$pid"; then
        return 0
    fi
    pid=$(_get_listen_pid)
    if _is_app_pid "$pid"; then
        echo "$pid" > "$PID_FILE"
        return 0
    fi
    rm -f "$PID_FILE"
    return 1
}

# 获取应用 PID (不存在则输出空)
get_pid() {
    local pid=""
    [ -f "$PID_FILE" ] && pid=$(cat "$PID_FILE" 2>/dev/null)
    if _is_app_pid "$pid"; then
        echo "$pid"
        return 0
    fi
    pid=$(_get_listen_pid)
    if _is_app_pid "$pid"; then
        echo "$pid" > "$PID_FILE"
        echo "$pid"
        return 0
    fi
    rm -f "$PID_FILE"
    return 1
}

# 从日志中提取最近的错误信息 (用于启动失败诊断)
# 只扫描日志尾部 200 行，避免历史错误干扰；过滤 INFO 行避免误匹配
extract_errors() {
    local log_file="${1:-$LOG_FILE}"
    [ -f "$log_file" ] || return 0
    tail -200 "$log_file" 2>/dev/null \
        | grep -E "ERROR|Exception|Caused by|APPLICATION FAILED|BUILD FAILURE" \
        | grep -v " INFO " \
        | tail -15 || true
}

# 启动应用 (含错误检测与输出)
start_app() {
    if is_running; then
        warn "应用已在运行 (PID: $(get_pid))"
        return 0
    fi

    # 检查 JAR 是否存在，不存在则先构建
    if [ ! -f "$JAR_FILE" ]; then
        warn "未找到 $JAR_FILE，先执行构建..."
        build_app || return 1
    fi

    # 清空旧日志
    > "$LOG_FILE"

    info "启动应用..."
    nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    local pid=$!
    echo "$pid" > "$PID_FILE"

    # 等待健康检查通过
    local retries=0
    while ! curl -sf "$HEALTH_URL" &>/dev/null 2>&1; do
        retries=$((retries + 1))
        if [ $retries -ge $START_TIMEOUT ]; then
            err "启动超时 (${START_TIMEOUT}s)！最近错误:"
            extract_errors | sed 's/^/    /'
            err "完整日志: ./run.sh logs"
            return 1
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            err "进程异常退出！最近错误:"
            extract_errors | sed 's/^/    /'
            err "完整日志: ./run.sh logs"
            return 1
        fi
        sleep 1
    done

    # 启动成功 - 展示面板
    echo ""
    echo -e "  ${GREEN}${BOLD}✔ 启动成功${NC}  ${DIM}(PID: $pid)${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}🌐 访问地址${NC}"
    echo ""
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/${NC}          ${DIM}← 首页 / 聊天${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/courses${NC}   ${DIM}← 课程管理${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/knowledge${NC} ${DIM}← 知识库${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/quiz${NC}      ${DIM}← 练习测验${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/plan${NC}      ${DIM}← 复习计划${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/stats${NC}     ${DIM}← 统计分析${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}📡 API 接口${NC}"
    echo ""
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/swagger-ui/index.html${NC}  ${DIM}← Swagger 文档${NC}"
    echo -e "    ${GREEN}${HEALTH_URL}${NC}       ${DIM}← 健康检查${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}🔧 常用命令${NC}"
    echo ""
    echo -e "    ${DIM}./run.sh stop${NC}       停止应用"
    echo -e "    ${DIM}./run.sh restart${NC}    重启应用"
    echo -e "    ${DIM}./run.sh logs${NC}       查看实时日志"
    echo -e "    ${DIM}./run.sh errors${NC}     查看最近错误"
    echo -e "    ${DIM}./run.sh status${NC}     查看服务状态"
    echo -e "    ${DIM}./run.sh doctor${NC}     全面体检"
    echo ""
}

# 停止应用 (优雅停止 → 强制终止)
stop_app() {
    if ! is_running; then
        warn "应用未在运行"
        rm -f "$PID_FILE"
        return 0
    fi

    local pid
    pid=$(get_pid)
    info "停止应用 (PID: $pid)..."

    kill "$pid" 2>/dev/null || true
    local retries=0
    while kill -0 "$pid" 2>/dev/null; do
        retries=$((retries + 1))
        if [ $retries -ge $STOP_TIMEOUT ]; then
            warn "优雅停止超时，强制终止..."
            kill -9 "$pid" 2>/dev/null || true
            break
        fi
        sleep 1
    done

    rm -f "$PID_FILE"
    ok "应用已停止"
}

# ============================================================
# 构建与测试
# ============================================================

build_app() {
    # 1. 构建前端
    if [ -d "$FRONTEND_DIR" ]; then
        info "构建前端 (Vue 3 SPA)..."
        if ! (cd "$FRONTEND_DIR" && npm install --silent && npm run build); then
            err "前端构建失败，请检查: cd $FRONTEND_DIR && npm run build"
            return 1
        fi
        ok "前端构建成功"
    else
        warn "未找到 $FRONTEND_DIR 目录，跳过前端构建"
    fi

    # 2. 构建后端
    info "编译后端..."
    if ! ./mvnw clean package -DskipTests -q; then
        err "后端编译失败，最近错误:"
        extract_errors | sed 's/^/    /'
        return 1
    fi
    ok "编译成功！JAR: $JAR_FILE"
    return 0
}

test_app() {
    info "运行后端测试..."
    ./mvnw test
}

# ============================================================
# 日志与错误
# ============================================================

show_logs() {
    if [ -f "$LOG_FILE" ] && [ -s "$LOG_FILE" ]; then
        echo -e "  ${DIM}按 Ctrl+C 退出日志查看${NC}"
        echo ""
        tail -f "$LOG_FILE"
    else
        warn "暂无日志 (应用可能未启动)"
    fi
}

show_errors() {
    if [ ! -f "$LOG_FILE" ] || [ ! -s "$LOG_FILE" ]; then
        warn "暂无日志"
        return 0
    fi
    local errors
    errors=$(extract_errors)
    if [ -z "$errors" ]; then
        ok "日志中未发现错误"
    else
        echo -e "  ${RED}${BOLD}最近错误:${NC}"
        echo ""
        echo "$errors" | sed 's/^/    /'
        echo ""
        info "完整日志: ./run.sh logs"
    fi
}

# ============================================================
# 状态检测与体检
# ============================================================

show_status() {
    echo ""
    echo -e "  ${BOLD}${CYAN}📊 服务状态${NC}"
    echo ""
    line

    # Docker
    echo -ne "  ${BOLD}Docker${NC}        "
    if check_docker 2>/dev/null; then ok "运行中"; else warn "未运行"; fi

    # PostgreSQL
    echo -ne "  ${BOLD}PostgreSQL${NC}    "
    if check_postgres; then
        ok "运行中 (pgvector, 5432)"
    else
        warn "未运行"
    fi

    # 端口冲突 (只检测监听 5432 的进程)
    echo -ne "  ${BOLD}端口冲突${NC}      "
    local local_pid
    local_pid=$(lsof -ti:5432 -sTCP:LISTEN -P -n 2>/dev/null | head -1)
    if [ -n "$local_pid" ]; then
        local proc_cmd
        proc_cmd=$(ps -p "$local_pid" -o command= 2>/dev/null)
        if echo "$proc_cmd" | grep -qi "postgres" && ! echo "$proc_cmd" | grep -q "docker"; then
            err "本地 PG 占用 5432 (PID: $local_pid)"
        else
            ok "无冲突"
        fi
    else
        ok "无冲突"
    fi

    # 应用进程
    echo -ne "  ${BOLD}应用${NC}          "
    if is_running; then
        ok "运行中 (PID: $(get_pid), 端口: $APP_PORT)"
    else
        warn "未运行"
    fi

    # 健康检查
    echo -ne "  ${BOLD}健康检查${NC}      "
    local health
    health=$(curl -sf "$HEALTH_URL" 2>/dev/null || true)
    if [ -n "$health" ]; then
        local status
        status=$(echo "$health" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
        if [ "$status" = "UP" ]; then
            ok "UP (${HEALTH_URL})"
        else
            warn "异常: $status"
        fi
    else
        warn "不可达"
    fi

    # Java
    echo -ne "  ${BOLD}Java${NC}          "
    if command -v java &>/dev/null; then
        echo -e "${GREEN}$(java -version 2>&1 | head -1 | sed 's/^[^"]*"\(.*\)".*/\1/')${NC}"
    else
        warn "未安装"
    fi

    echo ""
    line
    echo ""
}

# 全面体检: 环境/依赖/端口/配置/产物
doctor() {
    echo ""
    echo -e "  ${BOLD}${CYAN}🩺 全面体检${NC}"
    echo ""
    line
    echo ""

    local fail=0

    # 环境
    echo -e "  ${BOLD}${DIM}── 环境 ──${NC}"
    check_java || fail=1
    check_node || true
    check_docker || true
    echo ""

    # 数据库
    echo -e "  ${BOLD}${DIM}── 数据库 ──${NC}"
    if check_postgres; then
        ok "PostgreSQL + pgvector (5432)"
    else
        warn "PostgreSQL 未运行 (start 命令会自动启动)"
    fi
    check_local_postgres || true
    echo ""

    # 配置
    echo -e "  ${BOLD}${DIM}── 配置 ──${NC}"
    check_api_key || true
    echo ""

    # 构建产物
    echo -e "  ${BOLD}${DIM}── 构建产物 ──${NC}"
    if [ -f "$JAR_FILE" ]; then
        ok "后端 JAR: $JAR_FILE"
    else
        warn "后端 JAR 不存在，运行: ./run.sh build"
    fi
    if [ -f "src/main/resources/static/index.html" ]; then
        ok "前端产物: src/main/resources/static/index.html"
    else
        warn "前端产物不存在，运行: ./run.sh build"
    fi
    echo ""

    # 应用状态
    echo -e "  ${BOLD}${DIM}── 应用 ──${NC}"
    if is_running; then
        ok "应用运行中 (PID: $(get_pid))"
        local health
        health=$(curl -sf "$HEALTH_URL" 2>/dev/null || true)
        if [ -n "$health" ]; then
            ok "健康检查: $health"
        else
            warn "健康检查不可达"
        fi
    else
        warn "应用未运行"
    fi
    echo ""

    # 最近错误
    echo -e "  ${BOLD}${DIM}── 最近错误 ──${NC}"
    local errors
    errors=$(extract_errors)
    if [ -z "$errors" ]; then
        ok "无错误记录"
    else
        echo "$errors" | sed 's/^/    /'
    fi
    echo ""
    line
    echo ""

    if [ $fail -eq 0 ]; then
        ok "体检完成，环境正常"
    else
        warn "体检完成，存在需要关注的问题"
    fi
    echo ""
}

# ============================================================
# 帮助
# ============================================================

show_help() {
    echo ""
    echo -e "  ${BOLD}${CYAN}期末复习智能助手${NC}  ${DIM}一键管理脚本${NC}"
    echo ""
    line
    echo ""
    echo -e "  ${BOLD}用法${NC}  ./run.sh <命令>"
    echo ""
    echo -e "  ${BOLD}命令${NC}"
    echo "    start     一键启动 (环境检测 → PostgreSQL → 应用)"
    echo "    stop      停止应用"
    echo "    restart   重启应用"
    echo "    status    检测并输出当前所有服务状态"
    echo "    build     构建前端 + 后端"
    echo "    test      运行后端测试"
    echo "    logs      实时查看应用日志"
    echo "    errors    输出最近的应用错误"
    echo "    doctor    全面体检 (环境/依赖/端口/配置/产物)"
    echo "    help      显示帮助"
    echo ""
    echo -e "  ${BOLD}环境变量${NC}"
    echo "    DASHSCOPE_API_KEY    DashScope API 密钥 (AI 功能必需)"
    echo "    DB_USERNAME          数据库用户名 (默认: postgres)"
    echo "    DB_PASSWORD          数据库密码 (默认: postgres)"
    echo ""
    echo -e "  ${BOLD}快速开始${NC}"
    echo "    1. export DASHSCOPE_API_KEY=your-key"
    echo "    2. ./run.sh start"
    echo "    3. open http://localhost:8080"
    echo ""
}

# ============================================================
# 主逻辑
# ============================================================
case "${1:-help}" in
    start)
        echo ""
        echo -e "  ${BOLD}${CYAN}🚀 ${APP_NAME}${NC}"
        echo ""
        line
        echo ""
        check_java || exit 1
        ensure_postgres || exit 1
        start_app
        ;;
    stop)
        stop_app
        ;;
    restart)
        echo ""
        echo -e "  ${BOLD}${CYAN}🔄 重启 ${APP_NAME}${NC}"
        echo ""
        stop_app
        sleep 1
        ensure_postgres || exit 1
        start_app
        ;;
    status)
        show_status
        ;;
    doctor|check|diagnose)
        doctor
        ;;
    build)
        build_app
        ;;
    test)
        test_app
        ;;
    logs)
        show_logs
        ;;
    errors|error)
        show_errors
        ;;
    help|--help|-h)
        show_help
        ;;
    *)
        err "未知命令: $1"
        show_help
        exit 1
        ;;
esac