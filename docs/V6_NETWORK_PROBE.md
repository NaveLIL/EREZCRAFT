# V6 dedicated network probe

`V6DedicatedScenario` and `V6NetworkPeerSmoke` run one real dedicated server and
two independent vanilla TCP clients. All code is smoke-only and disabled by
default. These sources have been compiled together with standalone JDK 21 javac;
a compile is not evidence that the native network scenario passed.

## Preparation

Root owns Gradle run declarations, VM/program argument files, server properties,
EULA and launch. Use three distinct `.verification` profiles and explicit Java
argv arrays. Every process needs the same absolute
`interstice.v6NetworkEvidence` directory. Each launch needs fresh evidence.

| Process | Required properties and arguments |
|---|---|
| Live dedicated server | `interstice.v6NetworkScenario=true`; port 26593 |
| Peer one | `interstice.v6NetworkPeer=1`; username `V6PeerOne` |
| Peer two | `interstice.v6NetworkPeer=2`; username `V6PeerTwo` |

The server must have `online-mode=false`, `allow-flight=false` and root-prepared
`eula=true`. Small view/simulation distances are explicit test settings. The
clients use `ConnectScreen.startConnecting`, real loopback `InetSocketAddress`
connections and separate PID/UUID values. The connect host is explicitly IPv4
`127.0.0.1`, matching the isolated server bind and any local proxies; it does not
use hostname resolution that could select IPv6 `::1`. There is no integrated server or mock
connection. The launcher compares the reported PIDs with its three actual Popen
children.

The optional **peer-only** `interstice.v6NetworkPort` defaults to 26593 and accepts
only 26593, 26594 or 26595. Root can supply bounded raw TCP proxies on 26594/26595
to forward to the unchanged server port 26593. The peer does not inject or claim
latency; root records the proxy configuration and measurements separately.

## Twenty behavioral phases and reviewed guards

| Phase | Actual completion evidence |
|---|---|
| Two real clients | Two real TCP peers, distinct player UUIDs and three distinct JVM PIDs |
| SURGE entry | Prepared V6 Survival teleports, actual SURGE and both real shelter results |
| Roof mining | Native dig packets remove one roof and change only its player's shelter |
| Lens activation | Native block-use packet activates the physical prepared frame |
| Simultaneous portal entry | Native movement stops inside the portal for its 40-tick warmup; actual dimension-change arrivals overlap within a declared 40-server-tick window; one saved link and separate journey UUIDs |
| Echo return | Native block use after 80 ticks returns both players through the saved link |
| Shared beacon indicators | Two separate ordinary Shift-use copies bind the same public marker UUID |
| Paid spool attachment | Each native 32-block attachment pays exactly once |
| Controlled moving rope | Explicit prepared 8-block lines, then native sprint/jump/Shift from a side ledge; each player has at least 200 real owned controlled ticks with actual server position delta Y below -0.03125; held spool, Survival, connected TCP, no flying/noPhysics and finite force/step bounds |
| Physical obstruction | Actual prepared solid blocks intersect the rope rays and production physics detaches both links |
| Lift cargo and boarding | Native 27-slot cargo menu quick-moves exactly 26 stones; the other real client boards; no duplicate stones remain in player inventory |
| Lift motion | Native owned anchor use moves the actual passenger and cargo at least 10 blocks while consuming finite fuel |
| Lift stop | A second native use stops the actual anchor; current-phase acknowledgment |
| Guardian first target | Registered actual guardian warns for at least 12 observed server ticks without early damage; first player targeted, second genuinely crouches |
| Guardian second target | First player's real crouch releases the target; a later actual second-target warning is observed for at least 12 ticks |
| Death and respawn | Actual death creates exactly three marked diamond drops; native DeathScreen respawn; spent journey UUID/entitlement retained |
| Disconnect and reconnect | Actual second-peer TCP close/reconnect; first peer remains present; same UUID, client PID and spent entitlement |
| Chunk unload | Both players remote; watched lift chunk actually absent from loaded FULL chunks |
| Chunk reload | Actual old-world reload retains one lift UUID and 26 stones; original guardian entity is loaded again |
| Final receipts | Current final token receipt or same-session clean client disconnect, actual generation quiescence, normal dedicated halt |

All platforms, items, saved portal link, roofs, ownership/fuel, short rope and
operator tide are explicitly prepared disposable fixtures. This probe does not
claim natural Survival gathering, worldgen distribution or CPU population
equivalence. The side ledge lies outside the descending rope ray so the fixture
does not silently obstruct the intended episode. No player teleports occur
during counted rope motion. Vanilla floating counters are read only.

Commands are smoke-only `v6net hello <actualPID>`, `ack <token>` and final
`receipt <token>` for the two exact names. Late acknowledgments from a completed
phase are logged/ignored and cannot satisfy a newer phase. A final receipt must
use the final token. Block uses wait for the actual expected block/dimension;
the native result must consume the interaction. Unexpected disconnects record
the actual vanilla reason. Client closure and reconnect call
`ClientLevel.disconnect()` before `Minecraft.disconnect()`, then stop only the
own client. Neither gameplay nor anti-flight state is relaxed for the probe.

The server records actual known motion, per-tick position movement, longest
controlled run, total controlled drop, force and read-only floating-counter peak.
Position comparisons include the peer's observed server tick/token; mismatched
dimensions/phases or old asynchronous samples are distinguished. Three recent
same-phase rope snapshots with position error over eight blocks fail the phase.

## Cold restart of the same world

The successful live run writes `cold-baseline.json` from its actual server
objects before normal final disconnect/save. The input records the real world
directory and seed, all three old PIDs, anchor/lift UUID/owner/fuel/position and
cargo slots, beacon UUID/owner/name/fuel, guardian UUID/home/residency, portal UUID
and endpoints, and each full journey UUID/origin/entered/spent record. It is
eligible only when no behavioral phase was failed or open. Guardian timer values
are pre-save observations and are labelled as such.

After all three first-run processes exit normally, root launches
`V6DedicatedColdRestart` in a **new dedicated JVM** with the same server cwd,
`level-name` and world files. Use:

```text
interstice.v6NetworkColdRestart=true
interstice.v6NetworkScenario=false
interstice.v6NetworkColdInput=ABS_FIRST_EVIDENCE/cold-baseline.json
interstice.v6NetworkEvidence=ABS_NEW_COLD_EVIDENCE
```

The two clients retain their peer properties/names but use new JVMs and the new
evidence directory. Cold input and evidence must be separate. The helper requires
the first server's full pass and generation quiescence, compares the exact real
server/world directories and seed, then loads a bounded window of the existing
chunks. It places no blocks, spawns no substitute entity and injects no fuel.

Actual persisted objects must retain lift/anchor identity, one local lift, exact
26-stone slot contents, stopped state/fuel/position, beacon identity/name,
guardian UUID/home/residency, physical active portal/echo and all saved journey
records including the second player's spent entitlement. Beacon fuel must be
within the amount that could naturally burn during the measured old/new ticks.
Running guardian phases/timers are reported with their scope; exact equality to
the pre-save observation is not claimed. Two new real TCP clients then prove the
saved cargo through an ordinary reopened menu and the received beacon/guardian
UUIDs through actual client objects. Normal final closure/quiescence is required.

## Reports and launcher

- `v6-network-server.json`: actual live phase rows/events/PIDs/UUIDs, position and
  rope samples, failed/open criteria, and final quiescence.
- `peer-1.json`, `peer-2.json`: actual client PID/UUID, connect port, native actions,
  observed state ticks, actual disconnect reasons and own clean closure.
- `cold-baseline.json`: real first-run saved-object input, not a generated fixture.
- `v6-network-cold-server.json`: new PID and same-world checks, saved identities,
  native client cargo/entity evidence and cold quiescence.
- `launch.json`: actual child PIDs, normal exit codes and scoped aggregate results.

`minimum_network_passed` means only the real two-client connection and two
200-tick controlled rope episodes. It can remain true when a later phase fails.
Peer `passed` refers to the complete behavioral phase checks; server/launcher full
pass additionally requires final receipts, generation quiescence, matching actual
PIDs and all normal exits. Guardian not registered is an explicit open phase,
never a decorative substitute. Cold pass is a separate result. No compile,
receipt or minimum result is reported as a whole matrix pass.

`tools/run_v6_network_probe.py --manifest ABS_JSON` consumes only a root-prepared
manifest inside `.verification`. It performs no Gradle work, prepares no EULA or
server properties and force-kills no process. Direct development launches need
root-prepared ModDev classpath-provider output and an explicit `env.MOD_CLASSES`
entry for frozen `interstice%%ABS_FOLDER` outputs inside `.verification`. The
launcher permits only this environment override, validates every referenced
frozen directory, and changes only its own child environment. Example shape:

```json
{
  "mode": "live",
  "evidence": "D:/repos/Minecraft/EREZCRAFT/.verification/RUN/net-evidence",
  "server": {"cwd":".../.verification/RUN/server","log":".../.verification/RUN/server.log","env":{"MOD_CLASSES":"interstice%%ABS_FROZEN_CLASSES;interstice%%ABS_FROZEN_RESOURCES"},"argv":["D:/.../java.exe","@ABS_VM_ARGS","@ABS_PROGRAM_ARGS"]},
  "peer1": {"cwd":".../.verification/RUN/peer1","log":".../.verification/RUN/peer1.log","env":{"MOD_CLASSES":"interstice%%ABS_FROZEN_CLASSES;interstice%%ABS_FROZEN_RESOURCES"},"argv":["D:/.../java.exe","@ABS_VM_ARGS","@ABS_PROGRAM_ARGS"]},
  "peer2": {"cwd":".../.verification/RUN/peer2","log":".../.verification/RUN/peer2.log","env":{"MOD_CLASSES":"interstice%%ABS_FROZEN_CLASSES;interstice%%ABS_FROZEN_RESOURCES"},"argv":["D:/.../java.exe","@ABS_VM_ARGS","@ABS_PROGRAM_ARGS"]}
}
```

For cold, set `mode` to `cold`, add `cold_input` with the absolute first baseline,
keep the same server cwd, and supply fresh evidence/logs/argument files with the
cold flags. The launcher additionally requires the first launcher full pass and
all three exit codes zero. All three restarted PID values must differ.

On bounded timeout/error the launcher writes a same-session cooperative close
request and sends `stop` only to the stdin of its own created server. It waits
up to 60 seconds and explicitly reports remaining owned PIDs; it never kills
unrelated or stuck games. Native network, cold and proxy-latency outcomes remain
pending until root actually executes and reviews these runs.
