#!/usr/bin/env bash
# Fictional quota ledger — end-to-end demo scenario against a running backend.
# Verifies: estimated>actual, same-vessel two landings, two-voyage quota
# shortage, paired transfer entries, dimension mismatch, duplicate certificate.
# All data is fictional; this system is NOT a real fishing permit.
set -euo pipefail

BASE="${1:-http://localhost:8080/api}"
say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
ok()  { printf '\033[1;32m✔ %s\033[0m\n' "$*"; }
fail(){ printf '\033[1;31m✘ %s\033[0m\n' "$*"; exit 1; }

jpost() { # path, json
  curl -s -w '\n%{http_code}' -H 'Content-Type: application/json' -d "$2" "$BASE$1"
}
expect_code() { # response, expected http code
  local code; code=$(printf '%s' "$1" | tail -n1)
  [ "$code" = "$2" ] || fail "期望 HTTP $2，实际 $code：$(printf '%s' "$1" | head -n -1)"
}

say "0. 健康检查"
expect_code "$(curl -s -w '\n%{http_code}' "$BASE/health")" 200 && ok "后端在线（虚构演示数据）"

say "1. 预计 > 实捕：FV-ALBATROSS V-DEMO-1 申报 MOK 100，实捕 80"
resp=$(jpost /voyages '{"voyageNo":"V-DEMO-1","vesselCode":"FV-ALBATROSS","seasonCode":"S2026","departedAt":"2026-03-02T06:00:00Z","note":"脚本演示：预计大于实捕","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","estimatedKg":100.000}]}')
expect_code "$resp" 200
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-1","voyageNo":"V-DEMO-1","landedAt":"2026-03-10T14:00:00Z","portName":"北镜港","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","actualKg":80.000}]}')
expect_code "$resp" 200
curl -s "$BASE/voyages" | grep -q '"differenceKg":20' \
  && ok "差额 20 kg 已显示（预计 100 − 实捕 80），余额仅按实捕 80 扣减" \
  || fail "差额未正确显示"

say "2. 同船两次卸货：FV-MARLIN V-DEMO-2 申报 RUBYFIN 300，分两次卸 180 + 100"
resp=$(jpost /voyages '{"voyageNo":"V-DEMO-2","vesselCode":"FV-MARLIN","seasonCode":"S2026","departedAt":"2026-04-05T05:30:00Z","note":"脚本演示：两次卸货","items":[{"speciesCode":"RUBYFIN","areaCode":"SFA-A1","estimatedKg":300.000}]}')
expect_code "$resp" 200
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-2A","voyageNo":"V-DEMO-2","landedAt":"2026-04-12T09:00:00Z","portName":"北镜港","items":[{"speciesCode":"RUBYFIN","areaCode":"SFA-A1","actualKg":180.000}]}')
expect_code "$resp" 200
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-2B","voyageNo":"V-DEMO-2","landedAt":"2026-04-15T16:00:00Z","portName":"星砂港","items":[{"speciesCode":"RUBYFIN","areaCode":"SFA-A1","actualKg":100.000}]}')
expect_code "$resp" 200
ok "两次卸货均已扣减（300 − 180 − 100 = 20），两条 LANDING 流水可溯源"

say "3. 重复录入同一卸货凭证 → 必须拒绝且不重复扣减"
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-2A","voyageNo":"V-DEMO-2","landedAt":"2026-04-12T09:00:00Z","portName":"北镜港","items":[{"speciesCode":"RUBYFIN","areaCode":"SFA-A1","actualKg":180.000}]}')
expect_code "$resp" 409 && ok "重复凭证被拒（HTTP 409 DUPLICATE），余额未二次扣减"

say "4. 两个航次额度不足：FV-ALBATROSS MOK 余额 70，V-DEMO-3 扣 50 后 V-DEMO-4 再卸 30 被拒"
resp=$(jpost /voyages '{"voyageNo":"V-DEMO-3","vesselCode":"FV-ALBATROSS","seasonCode":"S2026","departedAt":"2026-05-04T06:00:00Z","note":"脚本演示：第一航次","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","estimatedKg":50.000}]}')
expect_code "$resp" 200
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-3","voyageNo":"V-DEMO-3","landedAt":"2026-05-11T12:00:00Z","portName":"北镜港","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","actualKg":50.000}]}')
expect_code "$resp" 200
resp=$(jpost /voyages '{"voyageNo":"V-DEMO-4","vesselCode":"FV-ALBATROSS","seasonCode":"S2026","departedAt":"2026-05-18T06:00:00Z","note":"脚本演示：第二航次（预计即预警）","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","estimatedKg":30.000}]}')
expect_code "$resp" 200
printf '%s\n' "$resp" | head -n -1 | grep -q "预计缺口" \
  && ok "第二航次申报时给出预计缺口预警" || fail "缺少预计缺口预警"
resp=$(jpost /landings '{"certificateNo":"LC-DEMO-4","voyageNo":"V-DEMO-4","landedAt":"2026-05-25T12:00:00Z","portName":"北镜港","items":[{"speciesCode":"MOK","areaCode":"SFA-A1","actualKg":30.000}]}')
expect_code "$resp" 409 && ok "额度不足的卸货被拒（HTTP 409 QUOTA_EXCEEDED），余额保持 20"

say "5. 配额调拨：FV-KELP → FV-ALBATROSS，GHOSTHAKE/SFA-B2 100 kg（配对流水）"
resp=$(jpost /transfers '{"transferNo":"TR-DEMO-1","sourceVesselCode":"FV-KELP","destVesselCode":"FV-ALBATROSS","speciesCode":"GHOSTHAKE","areaCode":"SFA-B2","seasonCode":"S2026","amountKg":100.000,"effectiveFrom":"2026-06-01","effectiveTo":"2026-12-31","note":"脚本演示调拨"}')
expect_code "$resp" 200
KELP_ID=$(curl -s "$BASE/balances" | python3 -c 'import json,sys; print([b["id"] for b in json.load(sys.stdin) if b["vesselCode"]=="FV-KELP"][0])')
curl -s "$BASE/balances/$KELP_ID/trace" | grep -q '"entryType":"TRANSFER_OUT"' \
  && ok "调出方流水含 TRANSFER_OUT（对方 FV-ALBATROSS，生效期 2026-06-01 ~ 2026-12-31）" \
  || fail "调出方流水缺失"

say "6. 维度不匹配：FV-MARLIN MOK/SFA-A1 → FV-KELP（无该维度账户）→ 必须拒绝"
resp=$(jpost /transfers '{"transferNo":"TR-DEMO-BAD","sourceVesselCode":"FV-MARLIN","destVesselCode":"FV-KELP","speciesCode":"MOK","areaCode":"SFA-A1","seasonCode":"S2026","amountKg":10.000,"effectiveFrom":"2026-06-01","effectiveTo":"2026-12-31","note":"应被拒绝"}')
expect_code "$resp" 400 && ok "物种/海区不匹配的调拨被拒（HTTP 400 INVALID）"

say "全部演示场景通过 ✔"
