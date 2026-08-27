"""Verifica de ponta a ponta a conexão com o MetaTrader 5 via MCP.

Sobe o servidor `mt5mcp` em stdio (o mesmo comando do `.mcp.json`), chama
`initialize` -> (`login`) -> `get_account_info` e imprime o resultado. Serve
para confirmar que a ligação funciona antes de a usar dentro do Claude Code.

SÓ FUNCIONA NO WINDOWS: o pacote `metatrader5` do PyPI publica wheels apenas
para `win_amd64`, e o terminal MetaTrader 5 tem de estar instalado e aberto.

Uso mais simples — se o terminal MT5 já está aberto e autenticado na conta
que quer usar, não precisa de credencial nenhuma:

    uv run --with fastmcp --with python-dotenv scripts/check_mt5_mcp.py

O script usa a conta em que o terminal já está ligado. Só precisa de definir
credenciais se quiser que ele troque de conta; nesse caso são precisas as
três, lidas do ambiente ou de um ficheiro `.env` (que o `.gitignore` exclui):

    MT5_LOGIN=12345678
    MT5_PASSWORD="a_sua_senha"
    MT5_SERVER="SuaCorretora-Demo"

`MT5_PATH` é sempre opcional; sem ela usa-se o caminho de instalação normal.

Estas variáveis são usadas por ESTE script, não pelo servidor MCP — o
servidor recebe os valores como argumentos das ferramentas `initialize` e
`login`, e ignora-as no ambiente.
"""

import asyncio
import os
import sys

from dotenv import load_dotenv
from fastmcp import Client
from fastmcp.client.transports import StdioTransport

DEFAULT_MT5_PATH = r"C:\Program Files\MetaTrader 5\terminal64.exe"

SERVER_COMMAND = "uvx"
SERVER_ARGS = [
    "--from",
    "git+https://github.com/Qoyyuum/mcp-metatrader5-server",
    "mt5mcp",
]


def _field(info: object, name: str) -> object:
    """Lê um campo do AccountInfo, venha ele como objeto ou como dicionário."""
    if isinstance(info, dict):
        return info.get(name, "?")
    return getattr(info, name, "?")


def _resolve_credentials() -> tuple[int, str, str] | None:
    """Lê as credenciais opcionais de login.

    Devolve None quando nenhuma foi definida — nesse caso usa-se a conta em
    que o terminal MT5 já está autenticado. Exigir as três em conjunto evita
    o caso silencioso de um login a meio configurar.
    """
    names = ("MT5_LOGIN", "MT5_PASSWORD", "MT5_SERVER")
    values = {name: os.getenv(name) for name in names}
    provided = [name for name, value in values.items() if value]

    if not provided:
        return None
    if len(provided) < len(names):
        faltam = ", ".join(name for name in names if not values[name])
        sys.exit(
            f"Login incompleto: falta {faltam}. Defina as três variáveis "
            f"({', '.join(names)}) para trocar de conta, ou nenhuma para usar "
            "a conta em que o terminal MT5 já está ligado."
        )

    login_raw = values["MT5_LOGIN"]
    try:
        login = int(login_raw)
    except ValueError:
        sys.exit(f"MT5_LOGIN tem de ser o número da conta, recebi: {login_raw!r}")

    return login, values["MT5_PASSWORD"], values["MT5_SERVER"]


async def main() -> int:
    load_dotenv()

    mt5_path = os.getenv("MT5_PATH") or DEFAULT_MT5_PATH
    credentials = _resolve_credentials()

    transport = StdioTransport(
        command=SERVER_COMMAND,
        args=SERVER_ARGS,
        env={"MT5_MCP_TRANSPORT": "stdio"},
    )

    try:
        return await _run_checks(
            client=Client(transport), mt5_path=mt5_path, credentials=credentials
        )
    except Exception as exc:  # noqa: BLE001 - queremos um diagnóstico legível
        print(f"Não foi possível falar com o servidor MCP: {exc}")
        print()
        print("Causa mais provável: o servidor não arrancou. Ele depende do pacote")
        print("`metatrader5`, que só tem wheels para Windows (win_amd64). Este script")
        print("e o servidor MCP têm de correr no MESMO Windows onde está o terminal MT5.")
        return 1


async def _run_checks(
    client: Client, mt5_path: str, credentials: tuple[int, str, str] | None
) -> int:
    async with client:
        tools = await client.list_tools()
        print(f"Servidor MCP ligado. {len(tools)} ferramentas disponíveis.")

        print(f"\ninitialize(path={mt5_path!r})")
        result = await client.call_tool("initialize", {"path": mt5_path})
        if not result.data:
            print("FALHOU. Verifique que:")
            print("  1. O terminal MetaTrader 5 está instalado e ABERTO")
            print("  2. MT5_PATH aponta para o terminal64.exe correto")
            print("  3. O terminal permite trading algorítmico (Ferramentas > Opções > Expert Advisors)")
            return 1
        print("  OK")

        if credentials is None:
            print("\nlogin() ignorado - a usar a conta em que o terminal MT5 já está ligado.")
        else:
            login, password, server = credentials
            print(f"\nlogin(login={login}, server={server!r})")
            result = await client.call_tool(
                "login", {"login": login, "password": password, "server": server}
            )
            if not result.data:
                print(
                    "FALHOU. Confirme número de conta, senha e nome exato do "
                    "servidor da corretora."
                )
                await client.call_tool("shutdown", {})
                return 1
            print("  OK")

        print("\nget_account_info()")
        info = (await client.call_tool("get_account_info", {})).data
        print(f"  conta:     {_field(info, 'login')}")
        print(f"  nome:      {_field(info, 'name')}")
        print(f"  corretora: {_field(info, 'company')}")
        print(f"  servidor:  {_field(info, 'server')}")
        print(f"  saldo:     {_field(info, 'balance')} {_field(info, 'currency')}")
        print(f"  alavanca:  {_field(info, 'leverage')}")

        await client.call_tool("shutdown", {})

    print("\nConexão MetaTrader 5 via MCP verificada com sucesso.")
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
