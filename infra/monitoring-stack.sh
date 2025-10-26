#!/bin/bash

###############################################################################
# Jetski SaaS - Monitoring Stack Management Script
# Gerencia o stack de observabilidade (Prometheus + Grafana + Loki)
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose-monitoring.yml"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

function print_header() {
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  Jetski Monitoring Stack${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
}

function print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

function print_error() {
    echo -e "${RED}✗${NC} $1"
}

function print_info() {
    echo -e "${YELLOW}ℹ${NC} $1"
}

function start_stack() {
    print_header
    print_info "Starting monitoring stack..."

    docker compose -f "$COMPOSE_FILE" up -d

    echo ""
    print_success "Monitoring stack started successfully!"
    echo ""
    print_info "Services:"
    echo "  - Grafana:      http://localhost:3000 (admin/admin)"
    echo "  - Prometheus:   http://localhost:9090"
    echo "  - Loki:         http://localhost:3100"
    echo "  - Alertmanager: http://localhost:9093"
    echo "  - Jaeger UI:    http://localhost:16686"
    echo "  - Promtail:     http://localhost:9080"
    echo ""
    print_info "Waiting for services to be ready..."
    sleep 10

    # Check if services are healthy
    if curl -s http://localhost:3000 > /dev/null 2>&1; then
        print_success "Grafana is ready!"
    else
        print_error "Grafana is not responding yet. Please wait a moment."
    fi

    if curl -s http://localhost:9090/-/healthy > /dev/null 2>&1; then
        print_success "Prometheus is ready!"
    else
        print_error "Prometheus is not responding yet. Please wait a moment."
    fi

    if curl -s http://localhost:3100/ready > /dev/null 2>&1; then
        print_success "Loki is ready!"
    else
        print_error "Loki is not responding yet. Please wait a moment."
    fi

    if curl -s http://localhost:9093/-/healthy > /dev/null 2>&1; then
        print_success "Alertmanager is ready!"
    else
        print_error "Alertmanager is not responding yet. Please wait a moment."
    fi

    if curl -s http://localhost:16686 > /dev/null 2>&1; then
        print_success "Jaeger is ready!"
    else
        print_error "Jaeger is not responding yet. Please wait a moment."
    fi

    if curl -s http://localhost:9080/ready > /dev/null 2>&1; then
        print_success "Promtail is ready!"
    else
        print_error "Promtail is not responding yet. Please wait a moment."
    fi
}

function stop_stack() {
    print_header
    print_info "Stopping monitoring stack..."

    docker compose -f "$COMPOSE_FILE" down

    print_success "Monitoring stack stopped!"
}

function restart_stack() {
    stop_stack
    echo ""
    start_stack
}

function status_stack() {
    print_header
    print_info "Monitoring stack status:"
    echo ""

    docker compose -f "$COMPOSE_FILE" ps
}

function logs_stack() {
    print_header
    print_info "Following monitoring stack logs (Ctrl+C to exit)..."
    echo ""

    docker compose -f "$COMPOSE_FILE" logs -f
}

function clean_stack() {
    print_header
    print_info "Cleaning monitoring stack (removing volumes)..."

    docker compose -f "$COMPOSE_FILE" down -v

    print_success "Monitoring stack cleaned (volumes removed)!"
}

function show_help() {
    print_header
    echo "Usage: $0 {start|stop|restart|status|logs|clean}"
    echo ""
    echo "Commands:"
    echo "  start    - Start full observability stack"
    echo "             (Prometheus + Grafana + Loki + Promtail + Alertmanager + Jaeger)"
    echo "  stop     - Stop monitoring stack"
    echo "  restart  - Restart monitoring stack"
    echo "  status   - Show status of services"
    echo "  logs     - Follow logs of all services"
    echo "  clean    - Stop and remove volumes (WARNING: deletes data)"
    echo ""
    echo "Services:"
    echo "  • Prometheus   - Metrics collection (port 9090)"
    echo "  • Grafana      - Visualization dashboards (port 3000)"
    echo "  • Loki         - Log aggregation (port 3100)"
    echo "  • Promtail     - Log shipping agent (port 9080)"
    echo "  • Alertmanager - Alert routing & notifications (port 9093)"
    echo "  • Jaeger       - Distributed tracing (port 16686)"
    echo ""
}

# Main script
case "${1:-}" in
    start)
        start_stack
        ;;
    stop)
        stop_stack
        ;;
    restart)
        restart_stack
        ;;
    status)
        status_stack
        ;;
    logs)
        logs_stack
        ;;
    clean)
        clean_stack
        ;;
    *)
        show_help
        exit 1
        ;;
esac
