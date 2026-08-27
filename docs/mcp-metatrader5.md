# Conexão com o MetaTrader 5 via MCP

Este repositório inclui a configuração para ligar um cliente MCP (Claude Code,
Claude Desktop, Cursor, Gemini CLI...) ao **MetaTrader 5**, usando o servidor
[`mcp-metatrader5-server`](https://github.com/Qoyyuum/mcp-metatrader5-server).

## Antes de mais: onde isto corre

**Só funciona no Windows.** O servidor MCP depende do pacote `metatrader5`
(fixado na versão `5.0.5735`), que publica wheels apenas para `win_amd64`.
Em Linux ou macOS a instalação falha logo na resolução de dependências:

```
Because metatrader5==5.0.5735 has no wheels with a matching platform tag
(e.g., `manylinux_2_39_x86_64`) [...] your requirements are unsatisfiable.
hint: Wheels are available for `metatrader5` (v5.0.5735) on the following
platform: `win_amd64`
```

Além disso, o pacote conversa com o terminal MetaTrader 5 **no mesmo
computador**, por IPC local — não há ligação remota. Ou seja: o cliente MCP,
o servidor MCP e o terminal MT5 têm de estar todos no mesmo Windows.

Se você usa Linux/macOS, as alternativas são uma VM ou VPS Windows, ou
escrever um serviço próprio que exponha o MT5 pela rede.

## Requisitos

- Windows com o **terminal MetaTrader 5 instalado e aberto**, já autenticado
  numa conta (demo ou real).
- No terminal: **Ferramentas → Opções → Expert Advisors →** permitir trading
  algorítmico.
- Python 3.12+ e [`uv`](https://docs.astral.sh/uv/) instalado (fornece o `uvx`).

## Configuração

O ficheiro [`.mcp.json`](../.mcp.json) na raiz do repositório já declara o
servidor. Ao abrir o Claude Code nesta pasta (no Windows), ele propõe ativar
o servidor `metatrader5`:

```json
{
  "mcpServers": {
    "metatrader5": {
      "command": "uvx",
      "args": [
        "--from",
        "git+https://github.com/Qoyyuum/mcp-metatrader5-server",
        "mt5mcp"
      ],
      "env": {
        "MT5_MCP_TRANSPORT": "stdio"
      }
    }
  }
}
```

O primeiro arranque demora (o `uvx` compila/descarrega as dependências);
os seguintes usam a cache.

### Por que não há credenciais no `.mcp.json`

Configurações que circulam pela internet costumam trazer um bloco assim:

```json
"env": {
  "MT5_LOGIN": "12345678",
  "MT5_PASSWORD": "sua_senha_aqui",
  "MT5_SERVER": "SuaCorretora-Demo",
  "MT5_PATH": "C:\\Program Files\\MetaTrader 5\\terminal64.exe"
}
```

**Essas quatro variáveis não fazem nada.** O servidor lê do ambiente apenas
`MT5_MCP_TRANSPORT`, `MT5_MCP_HOST` e `MT5_MCP_PORT` (ver `src/mcp_mt5/__init__.py`
no repositório do servidor). `MT5_LOGIN`, `MT5_PASSWORD`, `MT5_SERVER` e
`MT5_PATH` só aparecem no `test_client.py` — um script de exemplo do lado do
*cliente*, que nunca é executado pelo servidor.

As credenciais entram como **argumentos das ferramentas**, não pelo ambiente.
Manter a senha fora do `.mcp.json` também evita commitá-la por acidente.

## Como ligar (sequência das ferramentas)

A ordem importa — `initialize` tem de vir primeiro:

1. `initialize(path="C:\\Program Files\\MetaTrader 5\\terminal64.exe")`
   liga ao terminal já aberto.
2. `login(login=12345678, password="...", server="SuaCorretora-Demo")`
   **opcional** — se o terminal já está autenticado na conta que quer usar,
   pode saltar este passo e não precisa de escrever a senha em lado nenhum.
3. `get_account_info()` confirma que está ligado à conta certa.
4. `shutdown()` no fim.

## Verificar a ligação

O script [`scripts/check_mt5_mcp.py`](../scripts/check_mt5_mcp.py) executa
essa sequência de fora do cliente MCP e imprime os dados da conta.

Se o terminal MT5 já está aberto e autenticado na conta que quer usar, **não
precisa de credencial nenhuma** — o script salta o `login()` e usa a conta que
o terminal já tem. No Windows, sem sequer clonar o repositório:

```powershell
uv run --with fastmcp --with python-dotenv `
  "https://raw.githubusercontent.com/asHelder20/main/claude/metatrader5-mcp-connection-77owda/scripts/check_mt5_mcp.py"
```

Ou, a partir da raiz do repositório clonado:

```powershell
uv run --with fastmcp --with python-dotenv scripts/check_mt5_mcp.py
```

Só precisa de credenciais para **trocar de conta**. Nesse caso são precisas as
três, do ambiente ou de um ficheiro `.env` (que o `.gitignore` já exclui):

```env
MT5_LOGIN=12345678
MT5_PASSWORD="a_sua_senha"
MT5_SERVER="SuaCorretora-Demo"
```

`MT5_PATH` é opcional em qualquer dos casos — sem ela usa-se
`C:\Program Files\MetaTrader 5\terminal64.exe`.

Saída esperada:

```
Servidor MCP ligado. 26 ferramentas disponíveis.

initialize(path='C:\\Program Files\\MetaTrader 5\\terminal64.exe')
  OK

login() ignorado - a usar a conta em que o terminal MT5 já está ligado.

get_account_info()
  conta:     12345678
  ...

Conexão MetaTrader 5 via MCP verificada com sucesso.
```

## Ferramentas disponíveis

- **Conexão**: `initialize`, `login`, `shutdown`, `get_account_info`,
  `get_terminal_info`, `get_version`, `get_last_error`
- **Dados de mercado**: `get_symbols`, `get_symbols_by_group`, `get_symbol_info`,
  `get_symbol_info_tick`, `symbol_select`, `copy_rates_from_pos`,
  `copy_rates_from_date`, `copy_rates_range`, `copy_ticks_from_pos`,
  `copy_ticks_from_date`, `copy_ticks_range`
- **Trading**: `order_send`, `order_check`, `positions_get`,
  `positions_get_by_ticket`, `orders_get`, `orders_get_by_ticket`,
  `history_orders_get`, `history_deals_get`

São 26 ferramentas ao todo, mais 6 *resources* de referência
(`mt5://timeframes`, `mt5://tick_flags`, `mt5://order_types`,
`mt5://order_filling_types`, `mt5://order_time_types`, `mt5://trade_actions`)
com as constantes numéricas que `order_send` e `copy_rates_*` esperam.

## Relação com o bot deste repositório

Nenhuma, por enquanto. O bot em [`pocket_bot/`](../pocket_bot) opera na
**Pocket Option** por WebSocket (`BinaryOptionsToolsV2`) e não usa o MT5.
Esta configuração MCP é uma via separada, para analisar mercado e operar no
MetaTrader 5 a partir do assistente.

Se a ideia for reaproveitar a estratégia PSAR/RSI/Bollinger em contas MT5,
isso é trabalho à parte: `order_send` no MT5 é ordem de mercado com volume em
lotes, não uma opção binária CALL/PUT com expiração fixa — o módulo de risco
e o de execução teriam de ser reescritos.

## ⚠️ Risco

`order_send` **executa ordens reais**. Comece sempre numa conta demo e
confirme com `get_account_info()` em que conta está ligado antes de qualquer
operação.
