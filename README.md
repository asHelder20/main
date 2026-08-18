# Pocket Option Bot (EA)

Bot de trading automatizado ("EA") para a corretora **Pocket Option**, que
analisa vários pares em tempo real, escolhe automaticamente o de melhor
sinal a cada ciclo e executa a operação (CALL/PUT) sozinho, com limites de
risco configuráveis.

## ⚠️ Leia antes de usar

- **A Pocket Option não tem API oficial.** Este bot usa a biblioteca
  não-oficial [`BinaryOptionsToolsV2`](https://github.com/ChipaDevTeam/BinaryOptionsTools-v2),
  que reimplementa o protocolo WebSocket da corretora. Isso pode violar os
  termos de uso da corretora e pode parar de funcionar sem aviso se eles
  mudarem o protocolo.
- **Opções binárias são um produto de altíssimo risco.** É estatisticamente
  comum perder o capital investido. Nenhuma estratégia aqui é garantia de
  lucro.
- **O SSID dá acesso total à sua conta.** Nunca o compartilhe, nunca faça
  commit dele (o `.gitignore` já exclui o `.env`).
- Por padrão o bot está configurado para **conta real** (`PO_DEMO=false`),
  a pedido de quem configurou este projeto. Ainda assim, ele exige a
  variável `LIVE_TRADING_CONFIRMED=true` para ligar nesse modo — isso é
  proposital, como última trava antes de operar dinheiro real sem
  supervisão humana por entrada. **Recomendo fortemente rodar alguns dias
  em conta demo antes** (basta usar um SSID de sessão demo e deixar
  `LIVE_TRADING_CONFIRMED=false`).

## O algoritmo (pesquisa)

Antes de definir a estratégia, pesquisei o que bots de Pocket Option
costumam usar na prática. O achado mais concreto foi um bot open-source
amplamente referenciado, o
[`pocket_option_trading_bot`](https://github.com/VitalySvyatyuk/pocket_option_trading_bot),
cuja estratégia principal de indicador é o **Parabolic SAR (PSAR)**: ele
opera CALL/PUT na direção da tendência indicada pelo PSAR. Buscas gerais
sobre bots/sinais de Pocket Option confirmam o mesmo padrão do mercado:
RSI, MACD, Bandas de Bollinger e Stochastic como indicadores mais citados,
PSAR e cruzamento de médias para a direção da tendência, e martingale
(dobrar a aposta após perda) como técnica de gestão de banca comum — porém
constantemente descrita como alto risco e desaconselhada, inclusive pelo
próprio blog da Pocket Option.

Por isso a estratégia aqui reproduz o algoritmo do bot de referência
(PSAR) em vez de reinventar um, mas **sem martingale** — o gerenciamento
de risco usa stake fixo com limites diários (ver `risk.py`), que é a
prática recomendada pelas próprias fontes pesquisadas.

## Como funciona

1. **Conexão** (`pocket_bot/bot.py`): abre uma sessão autenticada via SSID
   usando `PocketOptionAsync`.
2. **Dados de mercado** (`pocket_bot/market_data.py`): para cada par
   monitorado, assina o feed `get_candles_live` (candles fechados + candle
   em formação, sem gaps) e mantém um `DataFrame` atualizado em memória.
3. **Estratégia / EA** (`pocket_bot/strategy.py` + `indicators.py`):
   a cada ciclo (`DECISION_INTERVAL_SECONDS`), calcula para cada par:
   - **Parabolic SAR** — sinal principal: entra CALL/PUT logo após o PSAR
     reverter (preço cruza para o outro lado do SAR), a mesma lógica do
     bot de referência pesquisado
   - **RSI (14)** — bloqueia a entrada quando a tendência que acabou de
     reverter já parece esgotada no mesmo sentido (sobrecompra para CALL,
     sobrevenda para PUT)
   - **Bandas de Bollinger (20, 2)** — filtro de volatilidade mínima, para
     não operar em mercado "parado"

   Cada par que gera sinal recebe um **score de 0 a 1** (baseado na
   distância entre preço e SAR). O bot escolhe o par com maior score entre
   todos os monitorados e só opera se o score ultrapassar
   `MIN_SIGNAL_SCORE`. É assim que o bot "escolhe os pares" automaticamente.
4. **Expirações por ativo**: a Pocket Option só aceita durações fixas por
   ativo (ex.: 5s/15s/30s/60s/180s/300s — o que você vê na plataforma como
   "M1", "M3", "M5"...). Ao conectar, o bot consulta `active_assets()` e
   descobre a lista real (`allowed_candles`) de cada par configurado; se
   `EXPIRY_SECONDS` não for uma opção válida para aquele par, ele usa a
   duração permitida mais próxima automaticamente (e avisa no log). Pares
   inativos no momento ou não encontrados na corretora são ignorados.
5. **Execução**: compra CALL/PUT via `client.buy`/`client.sell`, registra a
   operação em `trade_journal.csv` e depois confirma o resultado via
   `check_win`, atualizando o PnL do dia.
5. **Gestão de risco** (`pocket_bot/risk.py`): antes de cada entrada,
   verifica perda máxima diária, número máximo de operações no dia,
   operações simultâneas e cooldown após perda.

## Instalação

```bash
python3 -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

## Configuração

```bash
cp .env.example .env
```

Edite o `.env`:

1. **Obtenha o SSID**: faça login em pocketoption.com no navegador →
   DevTools (F12) → aba **Network** → filtre por `WS` → clique na conexão
   WebSocket → veja a primeira mensagem enviada, algo como
   `42["auth",{"session":"...","isDemo":1,...}]`. Copie o valor completo
   dessa mensagem (ou o cookie `ssid`, dependendo da versão da lib) para
   `PO_SSID`.
2. Ajuste `PO_PAIRS`, `STAKE_AMOUNT`, `EXPIRY_SECONDS` e os limites de
   risco (`MAX_DAILY_LOSS`, `MAX_TRADES_PER_DAY`, etc.) conforme seu
   perfil.
3. Todos os parâmetros da estratégia (PSAR, RSI, Bollinger,
   `MIN_SIGNAL_SCORE`) são ajustáveis no `.env` — os valores padrão são um
   ponto de partida razoável, não uma calibração testada em dados reais da
   Pocket Option. Rode em demo, observe os logs (nível `DEBUG` mostra os
   scores calculados a cada ciclo) e ajuste.

## Rodando

```bash
python main.py
```

O bot roda em loop até você interrompê-lo com `Ctrl+C` (encerramento
gracioso: para os feeds de dados antes de sair). Todas as entradas e
resultados ficam registrados em `trade_journal.csv`.

## Estrutura do projeto

```
main.py                    ponto de entrada
pocket_bot/
  config.py                carrega e valida o .env
  indicators.py             Parabolic SAR, RSI, Bandas de Bollinger
  strategy.py               a lógica de decisão (o "EA")
  market_data.py            feed de candles em tempo real por par
  risk.py                   limites de risco
  journal.py                registro CSV de operações
  bot.py                    orquestrador (conecta, decide, executa)
```

## Troubleshooting

A biblioteca `BinaryOptionsToolsV2` é mantida por terceiros e evolui
rápido. Se algum método citado aqui (`buy`, `sell`, `check_win`,
`get_candles_live`, `is_demo`, etc.) mudar de nome ou assinatura numa
versão nova, o erro aparecerá no log ao rodar o bot. Nesse caso, verifique
a versão instalada com `pip show BinaryOptionsToolsV2` e compare com os
exemplos em `examples/python/async/` do repositório oficial
(https://github.com/ChipaDevTeam/BinaryOptionsTools-v2).
