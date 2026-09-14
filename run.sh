#!/bin/bash
# ============================================================
#  期末复习智能助手 (AI Review Assistant) 启动脚本
#  用法: ./run.sh {start|stop|restart|status|build|test|logs|help}
# ============================================================

set -euo pipefail

# ---------- 配置 ----------
APP_NAME="ai-review-assistant"
APP_PORT=8080
APP_MAIN_CLASS="com.aiservice.aireviewassistant.AiReviewAssistantApplication"
PID_FILE=".app.pid"
LOG_FILE="app.log"
JAR_FILE="target/${APP_NAME}-0.0.1-SNAPSHOT.jar"

# ---------- 颜色与样式 ----------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'
NC='\033[0m'

info()  { echo -e "  ${BLUE}ℹ${NC}  $*"; }
ok()    { echo -e "  ${GREEN}✔${NC}  $*"; }
warn()  { echo -e "  ${YELLOW}⚠${NC}  $*"; }
err()   { echo -e "  ${RED}✖${NC}  $*"; }

# 分隔线
line()  { echo -e "${DIM}$(printf '%.0s─' {1..50})${NC}"; }

# 旋转等待动画
spinner() {
    local msg="$1" delay=0.15
    local chars='⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏'
    local i=0
    echo -ne "  ${CYAN}${chars:i++:1}${NC}  ${msg}\033[0K\r"
    while kill -0 "$2" 2>/dev/null; do
        echo -ne "  ${CYAN}${chars:i++:1}${NC}  ${msg}\033[0K\r"
        i=$(( (i + 1) % ${#chars} ))
        sleep "$delay"
    done
}

# ---------- 检测 Docker ----------
check_docker() {
    if ! command -v docker &>/dev/null; then
        warn "未检测到 Docker，请先安装 Docker Desktop"
        return 1
    fi
    if ! docker info &>/dev/null 2>&1; then
        warn "Docker 未运行，请启动 Docker Desktop"
        return 1
    fi
    return 0
}

# ---------- 检测 Java/Maven ----------
check_java() {
    if ! command -v java &>/dev/null; then
        err "未检测到 Java，请安装 JDK 17+"
        return 1
    fi
    local ver=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    if [ "$ver" -lt 17 ] 2>/dev/null; then
        err "需要 JDK 17+，当前版本: $ver"
        return 1
    fi
    ok "Java $(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)"/\1/')"
}

# ---------- PostgreSQL (Docker) ----------
check_postgres() {
    docker ps 2>/dev/null | grep -q "ai-review-postgres"
}

check_local_postgres() {
    local local_pg_pid
    local_pg_pid=$(lsof -ti:5432 -P -n 2>/dev/null | head -1)
    if [ -n "$local_pg_pid" ]; then
        local proc_name
        proc_name=$(ps -p "$local_pg_pid" -o comm= 2>/dev/null)
        if echo "$proc_name" | grep -qi "postgres"; then
            local proc_cmd
            proc_cmd=$(ps -p "$local_pg_pid" -o command= 2>/dev/null)
            if ! echo "$proc_cmd" | grep -q "docker"; then
                warn "本地 PostgreSQL 占用 5432 (PID: $local_pg_pid)"
                warn "请运行: sudo /Library/PostgreSQL/17/bin/pg_ctl -D /Library/PostgreSQL/17/data stop -m fast"
                return 1
            fi
        fi
    fi
    return 0
}

ensure_postgres() {
    if check_postgres; then
        if docker exec ai-review-postgres pg_isready -U postgres &>/dev/null 2>&1; then
            ok "PostgreSQL + pgvector (端口 5432)"
            return 0
        fi
    fi

    if ! check_docker; then
        err "需要 Docker Desktop 来运行 PostgreSQL"
        return 1
    fi

    check_local_postgres || true

    info "启动 PostgreSQL + pgvector..."
    docker-compose up -d postgres >/dev/null 2>&1

    local retries=0
    while ! docker exec ai-review-postgres pg_isready -U postgres &>/dev/null 2>&1; do
        retries=$((retries + 1))
        if [ $retries -ge 30 ]; then
            err "PostgreSQL 启动超时！运行: docker logs ai-review-postgres"
            return 1
        fi
        sleep 1
    done
    ok "PostgreSQL + pgvector (端口 5432)"
}

start_postgres() { ensure_postgres; }

stop_postgres() {
    if check_postgres; then
        info "停止 PostgreSQL..."
        docker-compose stop postgres >/dev/null 2>&1
        ok "PostgreSQL 已停止"
    fi
}

# ---------- 应用 ----------
# 判断指定 PID 是否是我们的 Java 应用进程
_is_app_pid() {
    local pid="$1"
    [ -z "$pid" ] && return 1
    if ! kill -0 "$pid" 2>/dev/null; then
        return 1
    fi
    local cmd
    cmd=$(ps -p "$pid" -o command= 2>/dev/null)
    # 必须是 java 进程，且命令行包含主类或目标 jar
    if echo "$cmd" | grep -qE "^[^ ]*java " && \
       (echo "$cmd" | grep -q "$APP_MAIN_CLASS" || echo "$cmd" | grep -q "$JAR_FILE"); then
        return 0
    fi
    return 1
}

# 只返回真正在本地监听 APP_PORT 的进程 PID
_get_listen_pid() {
    lsof -ti:"$APP_PORT" -sTCP:LISTEN -P -n 2>/dev/null | head -1
}

is_running() {
    local pid=""
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE" 2>/dev/null)
    fi
    if _is_app_pid "$pid"; then
        return 0
    fi
    # PID_FILE 失效，尝试从监听端口的进程中查找
    pid=$(_get_listen_pid)
    if _is_app_pid "$pid"; then
        echo "$pid" > "$PID_FILE"
        return 0
    fi
    rm -f "$PID_FILE"
    return 1
}

get_pid() {
    local pid=""
    if [ -f "$PID_FILE" ]; then
        pid=$(cat "$PID_FILE" 2>/dev/null)
    fi
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
}

start_app() {
    if is_running; then
        warn "应用已在运行 (PID: $(get_pid))"
        return 0
    fi

    # 清空旧日志
    > "$LOG_FILE"

    # 启动应用
    if [ -f "$JAR_FILE" ]; then
        nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
    else
        nohup ./mvnw spring-boot:run -DskipTests > "$LOG_FILE" 2>&1 &
    fi

    local pid=$!
    echo "$pid" > "$PID_FILE"

    # 带动画等待启动
    local retries=0
    while ! curl -sf "http://localhost:${APP_PORT}/actuator/health" &>/dev/null 2>&1; do
        retries=$((retries + 1))
        if [ $retries -ge 90 ]; then
            err "启动超时！运行 ./run.sh logs 查看日志"
            return 1
        fi
        if ! kill -0 "$pid" 2>/dev/null; then
            err "进程已退出！运行 ./run.sh logs 查看日志"
            return 1
        fi
        sleep 1
    done

    # 启动成功 - 展示完整面板
    echo ""
    echo -e "  ${GREEN}${BOLD}✔ 启动成功${NC}  ${DIM}(PID: $pid)${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}🌐 访问地址${NC}"
    echo ""
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/${NC}              ${DIM}← 首页 / 聊天${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/courses.html${NC}   ${DIM}← 课程管理${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/knowledge.html${NC} ${DIM}← 知识库${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/quiz.html${NC}      ${DIM}← 练习测验${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/plan.html${NC}      ${DIM}← 复习计划${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/stats.html${NC}     ${DIM}← 统计分析${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}📡 API 接口${NC}"
    echo ""
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/swagger-ui/index.html${NC}  ${DIM}← Swagger 文档${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/actuator/health${NC}       ${DIM}← 健康检查${NC}"
    echo -e "    ${GREEN}http://localhost:${APP_PORT}/actuator/prometheus${NC}   ${DIM}← Prometheus 指标${NC}"
    echo ""
    line
    echo -e "  ${BOLD}${CYAN}🔧 常用命令${NC}"
    echo ""
    echo -e "    ${DIM}./run.sh stop${NC}       停止应用"
    echo -e "    ${DIM}./run.sh restart${NC}    重启应用"
    echo -e "    ${DIM}./run.sh logs${NC}       查看实时日志"
    echo -e "    ${DIM}./run.sh status${NC}     查看服务状态"
    echo -e "    ${DIM}./run.sh stop-all${NC}   停止所有服务"
    echo ""
}

stop_app() {
    if ! is_running; then
        warn "应用未在运行"
        rm -f "$PID_FILE"
        return 0
    fi

    local pid=$(get_pid)
    info "停止应用 (PID: $pid)..."

    kill "$pid" 2>/dev/null || true
    local retries=0
    while kill -0 "$pid" 2>/dev/null; do
        retries=$((retries + 1))
        if [ $retries -ge 10 ]; then
            warn "强制终止..."
            kill -9 "$pid" 2>/dev/null || true
            break
        fi
        sleep 1
    done

    rm -f "$PID_FILE"
    ok "应用已停止"
}

stop_all() {
    stop_app
    stop_postgres
    ok "所有服务已停止"
}

build_app() {
    info "编译项目..."
    ./mvnw clean package -DskipTests -q
    if [ $? -eq 0 ]; then
        ok "编译成功！JAR: $JAR_FILE"
    else
        err "编译失败"
        return 1
    fi
}

test_app() {
    info "运行测试..."
    ./mvnw test
}

show_logs() {
    if [ -f "$LOG_FILE" ] && [ -s "$LOG_FILE" ]; then
        echo -e "  ${DIM}按 Ctrl+C 退出日志查看${NC}"
        echo ""
        tail -f "$LOG_FILE"
    else
        warn "暂无日志"
    fi
}

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

    # 本地 PG 冲突
    echo -ne "  ${BOLD}端口冲突${NC}      "
    local local_pid
    local_pid=$(lsof -ti:5432 -P -n 2>/dev/null | head -1)
    if [ -n "$local_pid" ]; then
        local proc_cmd
        proc_cmd=$(ps -p "$local_pid" -o command= 2>/dev/null)
        if ! echo "$proc_cmd" | grep -q "docker"; then
            err "本地 PG 占用 5432 (PID: $local_pid)"
        else
            ok "无冲突"
        fi
    else
        ok "无冲突"
    fi

    # 应用
    echo -ne "  ${BOLD}应用${NC}          "
    if is_running; then
        ok "运行中 (PID: $(get_pid), 端口: $APP_PORT)"
    else
        warn "未运行"
    fi

    # Java
    echo -ne "  ${BOLD}Java${NC}          "
    if command -v java &>/dev/null; then
        echo -e "${GREEN}$(java -version 2>&1 | head -1 | sed 's/.*"\(.*\)"/\1/')${NC}"
    else
        warn "未安装"
    fi

    echo ""
    line
    echo ""
}

show_help() {
    echo ""
    echo -e "  ${BOLD}${CYAN}期末复习智能助手${NC}  ${DIM}v0.0.1${NC}"
    echo ""
    line
    echo ""
    echo -e "  ${BOLD}用法${NC}  ./run.sh <命令>"
    echo ""
    echo -e "  ${BOLD}命令${NC}"
    echo "    start     启动 PostgreSQL + 应用"
    echo "    stop      停止应用"
    echo "    stop-all  停止应用 + PostgreSQL"
    echo "    restart   重启应用"
    echo "    build     编译打包"
    echo "    test      运行测试"
    echo "    logs      查看实时日志"
    echo "    status    查看服务状态"
    echo "    help      显示帮助"
    echo ""
    echo -e "  ${BOLD}环境变量${NC}"
    echo "    DASHSCOPE_API_KEY    DashScope API 密钥"
    echo "    DB_USERNAME          数据库用户名 (默认: postgres)"
    echo "    DB_PASSWORD          数据库密码 (默认: postgres)"
    echo ""
    echo -e "  ${BOLD}快速开始${NC}"
    echo "    1. export DASHSCOPE_API_KEY=your-key"
    echo "    2. ./run.sh start"
    echo "    3. open http://localhost:8080"
    echo ""
}

# ---------- 主逻辑 ----------
case "${1:-help}" in
    start)
        echo ""
        echo -e "  ${BOLD}${CYAN}🚀 ${APP_NAME}${NC}"
        echo ""
        line
        echo ""
        check_java || exit 1
        start_postgres || exit 1
        start_app
        ;;
    stop)
        stop_app
        ;;
    stop-all)
        stop_all
        ;;
    restart)
        echo ""
        echo -e "  ${BOLD}${CYAN}🔄 重启 ${APP_NAME}${NC}"
        echo ""
        stop_app
        sleep 1
        start_postgres || exit 1
        start_app
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
    status)
        show_status
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
