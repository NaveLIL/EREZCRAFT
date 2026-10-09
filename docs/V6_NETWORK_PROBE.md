# V6: настоящий dedicated server и два клиента

Подготовлены smoke-only `V6DedicatedScenario`, `V6NetworkPeerSmoke` и общий
`V6NetworkFiles`. Все зависимости были staged вне src и вместе прошли standalone
JDK21 javac до публикации в `src/smoke`. Нативный запуск ещё не выполнялся;
наличие этих исходников не закрывает сетевую приёмку.

Root подключает три отдельных runs и verifier hooks:

| Процесс | Настройки |
|---|---|
| `v6Dedicated` | server; `interstice.v6NetworkScenario=true`; отдельный `.verification` profile |
| `v6NetPeerOne` | client; `interstice.v6NetworkPeer=1`; username `V6PeerOne`; свой profile |
| `v6NetPeerTwo` | client; `interstice.v6NetworkPeer=2`; username `V6PeerTwo`; свой profile |

У всех процессов одинаковый абсолютный `interstice.v6NetworkEvidence` внутри
`.verification`. Сервер слушает localhost26593; Root отдельно готовит
`server.properties` с online-mode=false, allow-flight=false, небольшими view/
simulation distance и собственным `eula.txt`. Сохранения владельца не используются.
Клиенты вызывают штатный `ConnectScreen.startConnecting` с `ServerAddress` и
`ServerData`, не создают integrated server и не используют `TestPlayers`/Mock connection.

Сервер принимает smoke команды `v6net hello <actualPID>`, `ack <token>` и
`receipt <token>` только от двух указанных реальных имён. Проверяются dedicated
server/port, loopback InetSocketAddress, отдельные PID/UUID обоих клиентов и PID сервера.
Монотонный server-owned token предотвращает позднее подтверждение другой фазы.
`state.json` и отчёты пишутся атомарно; команды лишь координируют fixture.
Block use/mining, inventory clicks, посадка, движение/Shift и respawn выполняются
обычными клиентскими API, отправляющими настоящие пакеты.

## Фазы подготовленной сцены

1. Два настоящих TCP подключения и три разных JVM PID.
2. Подготовленный перенос обоих Survival игроков в V6 при настоящем SURGE под крыши.
3. PeerOne добывает свою крышу настоящими dig packets; shelter меняется только у него.
4. Клиентская линза активирует подготовленную настоящую раму.
5. Оба клиента входят в одну сохранённую портал-связь обычным движением/контактом.
6. Обычные echo use packets возвращают обоих через тот же UUID; права аварийного возврата
   остаются отдельными server-owned Journey UUID.
7. Два независимых индикатора через обычный Shift+RMB копируют публичный marker UUID.
8. Каждый клиент платит за обычную32-блочную собственную привязку spool use packet.
9. Перед физическим эпизодом явно устанавливается короткая8-блочная fixture line,
   как в прежнем испытании. Настоящие sprint/jump/Shift movement packets должны дать
   каждому игроку≥200 загруженных controlled moving ticks ниже vanilla floating threshold.
10. Реальное подготовленное препятствие размыкает обе линии через обычную production physics.
11. Настоящее cargo menu27 slots переносит26 предметов, другой клиент садится пассажиром.
12. Обычный anchor use двигает пассажира/груз с конечным топливом минимум10 блоков.
13. Второй обычный use packet останавливает платформу.
14. Если страж зарегистрирован, настоящий первый игрок получает предупреждение/цель,
    а приседающий второй остаётся мирным.
15. Приседание первого снимает конфликт; после cooldown второй получает новое предупреждение.
16. Настоящая смерть/три проверяемых drop items и клиентская кнопка respawn; потраченное
    серверное emergency entitlement не восстанавливается.
17. Настоящий disconnect/reconnect второго TCP клиента, прежний UUID/потраченное право.
18. Оба клиента удалены от fixture; watched chunk должен действительно выгрузиться.
19. Фактическая загрузка сохраняет ровно одну world-owned lift UUID и26 предметов.
20. Receipts/clean client disconnect, generation quiescence, обычный server halt.

Подготовленные pads, предметы, saved portal link, beacon/lift ownership/fuel, короткая
линия и operator tide перечислены как fixture. Это не естественная добыча, частота
руин, вход новичка или Survival прохождение. Если entity стража не зарегистрирован,
его фазы остаются `open`, а декоративная сущность не заменяет доказательство.
Cold server reboot и искусственная задержка отдельно помечены невыполненными.

`aboveGroundTickCount` читается только для диагностики. Тест не пишет floating
counters, не включает allow-flight, noPhysics или mayfly и не телепортирует игроков
во время counted rope-motion episode. Position error сравнивает асинхронный client
snapshot с текущим server position; при разных dimension он помечается несопоставимым.

## Отчёты и собственный launcher

- `v6-network-server.json` — реальные connections/UUID/PID, phases, positions, rope
  counters, события login/logout/respawn и generation quiescence.
- `peer-1.json`, `peer-2.json` — реальные client PID/UUID, native actions, состояния
  и собственный чистый disconnect.
- `launch.json` — actual Popen PID трёх процессов, exit codes и согласование с отчётами.

`minimum_network_passed` относится только к двум физическим TCP клиентам и двум
успешным200-tick rope episodes. Поздние failed/open фазы остаются видимыми;
`passed` полного предоставленного сценария требует всех его фаз. Root дополнительно
проверяет `generation_quiescent_before_halt`, peer clean disconnect и PID matching.

Опциональный `tools/run_v6_network_probe.py` принимает только root-prepared manifest
в `.verification`; он не запускает Gradle, не переписывает props/EULA и не убивает
процессы. Формат manifest:

```json
{
  "evidence": "D:/repos/Minecraft/EREZCRAFT/.verification/RUN/net-evidence",
  "server": {"cwd":".../.verification/RUN/server","log":".../.verification/RUN/server.log","argv":["D:/.../java.exe","@ABSOLUTE_PREPARED_VM_ARGS","@ABSOLUTE_PREPARED_PROGRAM_ARGS"]},
  "peer1": {"cwd":".../.verification/RUN/peer1","log":".../.verification/RUN/peer1.log","argv":["D:/.../java.exe","@ABSOLUTE_PREPARED_VM_ARGS","@ABSOLUTE_PREPARED_PROGRAM_ARGS"]},
  "peer2": {"cwd":".../.verification/RUN/peer2","log":".../.verification/RUN/peer2.log","argv":["D:/.../java.exe","@ABSOLUTE_PREPARED_VM_ARGS","@ABSOLUTE_PREPARED_PROGRAM_ARGS"]}
}
```

На bounded timeout launcher публикует отдельный собственный close request и посылает
`stop` только в stdin созданного им сервера. Клиенты закрывают собственный connection
через `ClientLevel.disconnect()` перед `Minecraft.disconnect()`. Force kill/Thread.stop
и действия над чужими PID отсутствуют. Сервер автоматически halt выполняет только
после обоих receipts или подтверждённых clean disconnect того же session и quiescence.
