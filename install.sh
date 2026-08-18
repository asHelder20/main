#!/usr/bin/env bash
# Instalador do Pocket Option Bot para uma VPS Linux (Ubuntu/Debian ou
# RHEL/CentOS/Fedora). Rode a partir da pasta do projeto já transferida
# para a VPS:
#
#   sudo bash install.sh
#
# É idempotente: rodar de novo atualiza o código e reinicia o serviço,
# sem mexer no .env/journal já existentes.
set -euo pipefail

APP_USER="pocketbot"
APP_DIR="/opt/pocket-bot"
SERVICE_NAME="pocket-bot"
SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { printf '\n==> %s\n' "$1"; }

if [[ $EUID -ne 0 ]]; then
  echo "Rode como root: sudo bash install.sh" >&2
  exit 1
fi

if [[ ! -f "$SRC_DIR/main.py" || ! -f "$SRC_DIR/requirements.txt" ]]; then
  echo "Rode este script de dentro da pasta do projeto (onde estão main.py e requirements.txt)." >&2
  exit 1
fi

# Escapa \, & e | (delimitador do sed) para inserir valores com segurança
# num s|padrao|substituicao| sem que caracteres do SSID quebrem o comando.
sed_escape() {
  local s=$1
  s=${s//\\/\\\\}
  s=${s//&/\\&}
  s=${s//|/\\|}
  printf '%s' "$s"
}

log "Detectando gerenciador de pacotes"
if command -v apt-get >/dev/null; then
  apt-get update -y
  DEBIAN_FRONTEND=noninteractive apt-get install -y python3 python3-venv python3-pip rsync
elif command -v dnf >/dev/null; then
  dnf install -y python3 python3-pip rsync
elif command -v yum >/dev/null; then
  yum install -y python3 python3-pip rsync
else
  echo "Gerenciador de pacotes não suportado. Instale manualmente: python3, python3-venv (ou equivalente), python3-pip, rsync." >&2
  exit 1
fi

log "Criando usuário de sistema '$APP_USER' (sem login interativo)"
if ! id "$APP_USER" &>/dev/null; then
  useradd --system --create-home --shell /usr/sbin/nologin "$APP_USER"
fi

log "Copiando o projeto para $APP_DIR"
mkdir -p "$APP_DIR"
rsync -a --delete \
  --exclude '.git' --exclude 'venv' --exclude '__pycache__' \
  --exclude '.env' --exclude 'trade_journal.csv' \
  "$SRC_DIR/" "$APP_DIR/"

log "Criando ambiente virtual e instalando dependências Python"
python3 -m venv "$APP_DIR/venv"
"$APP_DIR/venv/bin/pip" install --upgrade pip --quiet
"$APP_DIR/venv/bin/pip" install -r "$APP_DIR/requirements.txt" --quiet

ENV_FILE="$APP_DIR/.env"
if [[ -f "$ENV_FILE" ]]; then
  log ".env já existe em $APP_DIR — mantendo a configuração atual (não sobrescrevo credenciais)."
elif [[ -t 0 ]]; then
  log "Configuração inicial (.env)"
  cp "$SRC_DIR/.env.example" "$ENV_FILE"

  read -rsp "SSID da Pocket Option (veja o README para como obter; a digitação fica oculta): " PO_SSID
  echo
  read -rp "Esta é uma conta DEMO? [S/n]: " IS_DEMO
  IS_DEMO=${IS_DEMO:-S}
  read -rp "Valor por entrada em USD [1.0]: " STAKE
  STAKE=${STAKE:-1.0}
  read -rp "Perda máxima diária em USD [20.0]: " MAX_LOSS
  MAX_LOSS=${MAX_LOSS:-20.0}

  [[ "$STAKE" =~ ^[0-9]+([.][0-9]+)?$ ]] || { echo "Valor inválido, usando 1.0"; STAKE=1.0; }
  [[ "$MAX_LOSS" =~ ^[0-9]+([.][0-9]+)?$ ]] || { echo "Valor inválido, usando 20.0"; MAX_LOSS=20.0; }

  sed -i "s|^PO_SSID=.*|PO_SSID=$(sed_escape "$PO_SSID")|" "$ENV_FILE"
  sed -i "s|^STAKE_AMOUNT=.*|STAKE_AMOUNT=${STAKE}|" "$ENV_FILE"
  sed -i "s|^MAX_DAILY_LOSS=.*|MAX_DAILY_LOSS=${MAX_LOSS}|" "$ENV_FILE"

  if [[ "$IS_DEMO" =~ ^[Nn]$ ]]; then
    sed -i "s|^PO_DEMO=.*|PO_DEMO=false|" "$ENV_FILE"
    echo
    echo "Você indicou conta REAL. Por segurança, o bot só opera em conta real"
    echo "se a corretora confirmar (pelo próprio SSID) que a conta é real E a"
    echo "variável LIVE_TRADING_CONFIRMED=true estiver no .env."
    read -rp "Confirmar operação com DINHEIRO REAL agora? Digite CONFIRMO: " CONFIRM
    if [[ "$CONFIRM" == "CONFIRMO" ]]; then
      sed -i "s|^LIVE_TRADING_CONFIRMED=.*|LIVE_TRADING_CONFIRMED=true|" "$ENV_FILE"
    else
      echo "Não confirmado. Deixando LIVE_TRADING_CONFIRMED=false — o bot vai"
      echo "recusar operar em conta real até você editar $ENV_FILE manualmente."
    fi
  else
    sed -i "s|^PO_DEMO=.*|PO_DEMO=true|" "$ENV_FILE"
  fi

  if [[ -z "$PO_SSID" ]]; then
    echo
    echo "Nenhum SSID informado. Edite $ENV_FILE antes de iniciar o serviço."
  fi
else
  log "Sessão não-interativa: criando .env a partir do exemplo (preencha PO_SSID manualmente antes de iniciar)"
  cp "$SRC_DIR/.env.example" "$ENV_FILE"
fi

chown -R "$APP_USER:$APP_USER" "$APP_DIR"
chmod 600 "$ENV_FILE"

log "Instalando serviço systemd"
cp "$APP_DIR/deploy/pocket-bot.service" "/etc/systemd/system/${SERVICE_NAME}.service"
systemctl daemon-reload

if grep -q '^PO_SSID=$' "$ENV_FILE" 2>/dev/null || ! grep -q '^PO_SSID=.' "$ENV_FILE" 2>/dev/null; then
  systemctl enable "$SERVICE_NAME" >/dev/null
  log "Instalação concluída, mas SEM SSID configurado."
  echo "Edite $ENV_FILE e depois rode: systemctl start $SERVICE_NAME"
else
  systemctl enable --now "$SERVICE_NAME"
  log "Instalação concluída e serviço iniciado."
fi

cat <<EOF

Status:  systemctl status $SERVICE_NAME
Logs:    journalctl -u $SERVICE_NAME -f
Config:  $ENV_FILE (edite e rode 'systemctl restart $SERVICE_NAME' para aplicar)

Para atualizar depois: transfira o código novo por cima desta pasta e
rode 'sudo bash install.sh' de novo (o .env e o trade_journal.csv atuais
são preservados).
EOF
