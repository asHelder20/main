# Pocket Option Bot — Android (app nativa)

App Android (Kotlin) que fala diretamente com o protocolo WebSocket da
Pocket Option e roda o mesmo algoritmo (EA) do bot em Python deste
repositório: reversão do Parabolic SAR como sinal principal, RSI como
filtro de exaustão e largura das Bandas de Bollinger como filtro de
volatilidade mínima.

## Por que isto não é só "o bot Python empacotado"

A `BinaryOptionsToolsV2` que o bot em Python usa é uma extensão Rust
compilada, publicada só para Linux/macOS/Windows de desktop — não existe
build para Android. Não há como reaproveitar essa dependência aqui. Em vez
disso, esta app implementa o protocolo WebSocket da Pocket Option
diretamente em Kotlin (via OkHttp).

## ⚠️ O que foi verificado e o que NÃO foi

Este projeto foi construído sem acesso a SDK Android, emulador ou
dispositivo físico — não consegui compilar nem correr a app Android em si
a partir daqui. Fui o mais cuidadoso possível para compensar isso, mas é
importante saberes exatamente onde está a fronteira entre o que é testado
e o que não é, já que isto mexe com dinheiro:

**Testado de verdade** (módulo `core`, Kotlin puro sem dependências
Android, compilado e com testes automatizados a correr — `36 testes,
0 falhas`):
- Indicadores (PSAR, RSI, Bollinger) — mesmas fórmulas do bot Python
- A estratégia (reversão do PSAR + filtros) e a escolha do melhor sinal
  entre pares
- Gestão de risco (limite diário, cooldown após perda, operações
  simultâneas, reinício ao virar o dia)
- O parsing do framing Engine.IO/Socket.IO (frames de abertura, ping/pong,
  eventos nomeados, deteção de SSID inválido)
- A construção exata das mensagens enviadas à corretora (`changeSymbol`,
  `openOrder`, `loadHistoryPeriod`) e o parsing das respostas (saldo,
  confirmação de ordem, candles históricos, ticks)
- O agregador que constrói candles OHLC a partir de ticks crus
- A resolução de expiração por ativo

Corre `gradle :core:test` dentro de `android/` para ver isto por ti mesmo.

**NÃO testado** (módulo `app`, código Android — revisto manualmente com
cuidado, mas nunca compilado nem executado):
- O comportamento real da troca de mensagens com os servidores da Pocket
  Option (o protocolo foi extraído lendo o código-fonte de um cliente
  Python não-oficial, não observado ao vivo a partir daqui - ver fonte
  abaixo)
- O ciclo de vida do `Service`/`Activity`, permissões em runtime,
  comportamento em segundo plano em diferentes versões/fabricantes de
  Android
- Se a UI compila e reage como esperado

**Por isso**: antes de confiar nisto com dinheiro real, testa
extensivamente em conta demo, acompanhando os logs na própria app, durante
vários dias, e revê o `trade_journal.csv` gerado (`Armazenamento internal
do app > files > trade_journal.csv`, acessível via Android Studio's Device
File Explorer ou `adb`).

## Fonte do protocolo

O protocolo (URLs dos servidores WebSocket, handshake Engine.IO/Socket.IO,
formato exato das mensagens de auth/subscrição/ordens) foi extraído lendo
o código-fonte real de
[Mastaaa1987/PocketOptionAPI-v2](https://github.com/Mastaaa1987/PocketOptionAPI-v2)
(cliente Python não-oficial, puro Python — dá para ler o protocolo
diretamente, ao contrário da lib Rust usada pelo bot em Python deste
repositório). Não foi inventado nenhum formato de mensagem; onde a fonte
não deixava algo claro (ver limitações abaixo), optei por não implementar
em vez de arriscar um palpite.

## Limitações conhecidas (deliberadas)

- **Sem confirmação de resultado por operação.** Não encontrei, na fonte
  lida, uma mensagem confirmada de "esta operação específica fechou assim"
  empurrada pelo servidor sem ambiguidade. Em vez de arriscar um formato
  inventado, o lucro/prejuízo é **inferido comparando o saldo antes e
  depois de cada operação expirar** (o campo `balance` é confirmadamente
  empurrado pelo servidor). Por isso o limite de **operações simultâneas
  está fixo em 1** no código (não exposto na UI) — com mais de uma
  operação aberta ao mesmo tempo, essa inferência por saldo deixa de ser
  confiável.
- **Sem validação de expiração por ativo.** O bot em Python consulta
  `active_assets()` para saber que durações cada par aceita e ajusta
  automaticamente. Não encontrei o equivalente cru desse pedido na fonte
  lida, então a app envia a expiração escolhida diretamente — se não for
  válida para o ativo, a corretora deve rejeitar a ordem (sem confirmação
  de erro tratada especificamente). Prefere durações comuns (5, 15, 30,
  60, 180, 300s) e confirma empiricamente em demo.
- **Backfill de histórico sequencial.** A resposta de `loadHistoryPeriod`
  não identifica a que ativo pertence, então a app só pede o histórico de
  um par de cada vez (o próprio cliente de referência tem a mesma
  limitação). A subscrição de ticks ao vivo, essa sim, já acontece em
  paralelo para todos os pares.

## Estrutura

```
android/
  core/    módulo Kotlin puro (sem Android) - protocolo, estratégia, risco
           testado com `gradle :core:test`
  app/     módulo Android - UI, Service em primeiro plano, ligação real
```

## Como compilar e instalar

Precisas do [Android Studio](https://developer.android.com/studio)
(inclui o SDK necessário).

1. Abre a pasta `android/` no Android Studio ("Open" → seleciona a pasta).
2. Deixa o Gradle sincronizar (vai pedir para descarregar o Android SDK
   34 e as dependências na primeira vez).
3. Liga o telemóvel por USB com a depuração USB ativada, ou usa um
   emulador.
4. `Run ▶` para instalar e abrir a app.

Ou por linha de comandos, com o Android SDK já instalado:
```bash
cd android
./gradlew installDebug   # depois de rodar `gradle wrapper` uma vez, ou usa o gradle do Android Studio
```

## Usar a app

1. Abre a app, cola o SSID (ver instruções no README principal do
   repositório sobre como obter), ajusta pares/stake/expiração.
2. Para conta real: marca "Confirmo operar com dinheiro REAL" - mesmo
   assim, a app só liga o modo real se a própria corretora confirmar (via
   `isDemo` na resposta de saldo) que a conta é mesmo real.
3. `Iniciar` - a app pede permissão de notificações (Android 13+) e arranca
   um serviço em primeiro plano (ícone persistente na barra de notificação,
   com botão "Parar").
4. Acompanha os logs no ecrã principal.

**Para deixar a correr em segundo plano de forma fiável**: em telemóveis
Xiaomi/Huawei/Samsung/Oppo, etc., desativa a otimização de bateria para
esta app nas definições do sistema — gestores de bateria agressivos destes
fabricantes podem matar o serviço mesmo sendo "foreground service".
