#!/bin/bash
# 운영 ALB e2e smoke test — 옵션 A 후 재검증 (2026-05-22)
# 실행: bash scripts/e2e-smoke-prod.sh
BASE="${BASE:-http://naengo-api-server-alb-176175450.ap-northeast-2.elb.amazonaws.com}"
echo "BASE=$BASE"
TS=$(date +%s)
EMAIL_A="e2e_${TS}_a@naengo.test"
EMAIL_B="e2e_${TS}_b@naengo.test"
NICK_A="e2eA_${TS}"
NICK_B="e2eB_${TS}"
PASS_OLD="oldPw12345A"
PASS_NEW="newPw67890B"
PASS=0
FAIL=0

log() { printf "[%-2s] %s\n" "$1" "$2"; }
check() {
  if [ "$2" = "$3" ]; then
    log "OK" "$1 $4 -> $3"
    PASS=$((PASS+1))
  else
    log "XX" "$1 $4 -> expected=$2 actual=$3"
    FAIL=$((FAIL+1))
  fi
}
contains() {
  if echo "$2" | grep -q "$3"; then
    log "OK" "$1 $4"
    PASS=$((PASS+1))
  else
    log "XX" "$1 $4 (body=$2)"
    FAIL=$((FAIL+1))
  fi
}
absent() {
  if echo "$2" | grep -q "$3"; then
    log "XX" "$1 $4 (found '$3' in body)"
    FAIL=$((FAIL+1))
  else
    log "OK" "$1 $4"
    PASS=$((PASS+1))
  fi
}

# 1 health
S=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/")
check 01 "200" "$S" "GET /"

# 2 signup A
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/v1/auth/signup" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_OLD\",\"nickname\":\"$NICK_A\"}")
S=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check 02 "201" "$S" "signup A"
TOKEN_A=$(echo "$BODY" | grep -o '"access_token":"[^"]*"' | sed 's/.*":"//;s/"//')
USER_ID_A=$(echo "$BODY" | grep -o '"user_id":[0-9]*' | sed 's/"user_id"://')
log "in" "  TOKEN_A len=${#TOKEN_A} USER_ID_A=$USER_ID_A"

# 3 duplicate signup -> 409
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/signup" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_OLD\",\"nickname\":\"$NICK_A\"}")
check 03 "409" "$S" "duplicate signup"

# 4 validation fail (short pw)
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/signup" -H "Content-Type: application/json" -d "{\"username\":\"x@x.com\",\"password\":\"1\",\"nickname\":\"x\"}")
check 04 "400" "$S" "VALIDATION_FAILED"

# 5 login
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_OLD\"}")
S=$(echo "$RESP" | tail -n1)
check 05 "200" "$S" "login"
TOKEN_A=$(echo "$RESP" | sed '$d' | grep -o '"access_token":"[^"]*"' | sed 's/.*":"//;s/"//')

# 6 login wrong pw -> 401
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"wrongpw9999\"}")
check 06 "401" "$S" "login wrong pw"

# 7 unauthenticated /users/me -> 401
S=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/users/me")
check 07 "401" "$S" "users/me no auth"

# 8 bearer auth /users/me
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/v1/users/me" -H "Authorization: Bearer $TOKEN_A")
S=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check 08 "200" "$S" "users/me bearer"
contains 08b "$BODY" "\"username\":\"$EMAIL_A\"" "username matches signup"
contains 08c "$BODY" "\"provider\":\"LOCAL\"" "provider=LOCAL"
contains 08d "$BODY" "\"is_active\":true" "is_active=true"
absent   08e "$BODY" "deleted_at" "deleted_at not exposed (option A)"

# 9 nickname change
S=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/v1/users/me" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d "{\"nickname\":\"${NICK_A}_v2\"}")
check 09 "200" "$S" "PATCH /users/me nickname"

# 10 signup B
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/v1/auth/signup" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_B\",\"password\":\"$PASS_OLD\",\"nickname\":\"$NICK_B\"}")
S=$(echo "$RESP" | tail -n1)
check 10 "201" "$S" "signup B"
TOKEN_B=$(echo "$RESP" | sed '$d' | grep -o '"access_token":"[^"]*"' | sed 's/.*":"//;s/"//')

# 11 nickname conflict
S=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/v1/users/me" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_B" -d "{\"nickname\":\"${NICK_A}_v2\"}")
check 11 "409" "$S" "nickname conflict"

# 12 password change ok
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/users/me/password" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d "{\"current_password\":\"$PASS_OLD\",\"new_password\":\"$PASS_NEW\"}")
check 12 "204" "$S" "password change ok"

# 13 password change wrong current -> 401
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/users/me/password" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d "{\"current_password\":\"wrongpw9999\",\"new_password\":\"yetanotherPw123\"}")
check 13 "401" "$S" "password change wrong current"

# 14 login with new pw
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_NEW\"}")
check 14 "200" "$S" "login w/ new pw"

# 15 profile GET empty
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/v1/users/me/profile" -H "Authorization: Bearer $TOKEN_A")
S=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check 15 "200" "$S" "profile GET empty"
contains 15b "$BODY" "\"user_input\":\[\]" "user_input=[]"

# 16 profile PATCH
S=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/v1/users/me/profile" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d '{"user_input":["shrimp allergy","spicy korean"]}')
check 16 "200" "$S" "profile PATCH"

# 17 profile GET after patch
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/v1/users/me/profile" -H "Authorization: Bearer $TOKEN_A")
S=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check 17 "200" "$S" "profile GET after patch"
contains 17b "$BODY" "shrimp allergy" "user_input contains shrimp allergy"

# 18 preferences GET
RESP=$(curl -s -w "\n%{http_code}" "$BASE/api/v1/users/me/preferences" -H "Authorization: Bearer $TOKEN_A")
S=$(echo "$RESP" | tail -n1)
BODY=$(echo "$RESP" | sed '$d')
check 18 "200" "$S" "preferences GET"
contains 18b "$BODY" "user_input" "preferences contains user_input"

# 19 preferences PATCH partial
S=$(curl -s -o /dev/null -w "%{http_code}" -X PATCH "$BASE/api/v1/users/me/preferences" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_A" -d '{"cooking_skill":"easy","preferred_cooking_time":20}')
check 19 "200" "$S" "preferences PATCH"

# 20 kakao social with fake token -> 401
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/social/kakao" -H "Content-Type: application/json" -d '{"access_token":"fake_invalid_token_xyz"}')
check 20 "401" "$S" "kakao bad token"

# 21 logout
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/logout" -H "Authorization: Bearer $TOKEN_A")
check 21 "204" "$S" "logout"

# 22 idempotent logout (no token)
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/logout")
check 22 "204" "$S" "logout idempotent"

# 23 cookie-based auth
TMP=$(mktemp)
curl -s -c "$TMP" -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_NEW\"}" > /dev/null
S=$(curl -s -b "$TMP" -o /dev/null -w "%{http_code}" "$BASE/api/v1/users/me")
check 23 "200" "$S" "cookie-based auth"
rm -f "$TMP"

# 24 withdraw
S=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE/api/v1/users/me" -H "Authorization: Bearer $TOKEN_A")
check 24 "204" "$S" "withdraw"

# 25 same token after withdraw -> 401
S=$(curl -s -o /dev/null -w "%{http_code}" "$BASE/api/v1/users/me" -H "Authorization: Bearer $TOKEN_A")
check 25 "401" "$S" "withdrawn token blocked"

# 26 same username login after withdraw -> 401 (anonymized)
S=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$EMAIL_A\",\"password\":\"$PASS_NEW\"}")
check 26 "401" "$S" "withdrawn login blocked"

echo
echo "==========================================="
echo "  PASS=$PASS  FAIL=$FAIL  total=$((PASS+FAIL))"
echo "==========================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
