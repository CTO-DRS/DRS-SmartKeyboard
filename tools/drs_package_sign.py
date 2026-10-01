#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS M0.3 — ed25519 package signing tool (RFC 8032).

Pure-Python ed25519 (reference-style, on affine twisted-Edwards arithmetic)
with a MANDATORY self-test on the official RFC 8032 §7.1 vectors (TEST 1–3)
that runs before every signing operation — a tool that cannot prove itself
never touches the release seed.

Commands:
  selftest                        Run the RFC vectors (exit 1 on failure).
  pubkey --seed-file PATH         Print the public key hex for a seed file.
  sign FILE --seed-file PATH      Print signature hex of FILE's raw bytes
                                  (self-test runs first, always).
  sign-catalog --seed-file PATH   Sign every packages/*.flex listed in
                                  packages/manifest.json and write the
                                  signature + pubkey_id fields back into the
                                  catalog (fail-closed: any failure aborts
                                  without writing).
  verify FILE SIG-HEX --pubkey HEX
                                  Verify a signature over FILE bytes.

The seed file must stay OUTSIDE git (.local-secrets/ is ignored on purpose).
"""
import argparse
import hashlib
import json
import os
import sys

# ----------------------------------------------------------------------------
# Curve constants (RFC 8032 §5.1)
# ----------------------------------------------------------------------------
p = 2**255 - 19
L = 2**252 + 27742317777372353535851937790883648493
d = (-121665 * pow(121666, p - 2, p)) % p
I = pow(2, (p - 1) // 4, p)  # sqrt(-1) mod p


def _xrecover(y):
    xx = (y * y - 1) * pow(d * y * y + 1, p - 2, p)
    x = pow(xx, (p + 3) // 8, p)
    if (x * x - xx) % p != 0:
        x = (x * I) % p
    if (x * x - xx) % p != 0:
        raise ValueError("invalid point encoding: y not on curve")
    if x % 2 != 0:
        x = p - x
    return x


By = (4 * pow(5, p - 2, p)) % p
Bx = _xrecover(By)
B = (Bx, By)
IDENTITY = (0, 1)


def _edwards_add(P1, P2):
    x1, y1 = P1
    x2, y2 = P2
    x3 = (x1 * y2 + x2 * y1) * pow(1 + d * x1 * x2 * y1 * y2, p - 2, p) % p
    y3 = (y1 * y2 + x1 * x2) * pow(1 - d * x1 * x2 * y1 * y2, p - 2, p) % p
    return (x3, y3)


def _scalarmult(point, scalar):
    result = IDENTITY
    base = point
    while scalar > 0:
        if scalar & 1:
            result = _edwards_add(result, base)
        base = _edwards_add(base, base)
        scalar >>= 1
    return result


def _encodepoint(point):
    x, y = point
    out = bytearray(y.to_bytes(32, "little"))
    if x % 2 != 0:
        out[31] |= 0x80
    return bytes(out)


def _decodepoint(data):
    raw = bytearray(data)
    sign = raw[31] & 0x80
    raw[31] &= 0x7F
    y = int.from_bytes(bytes(raw), "little")
    if y >= p:
        raise ValueError("invalid point: y >= p")
    x = _xrecover(y)
    if (x % 2 != 0) != (sign != 0):
        x = p - x
    return (x, y)


def _clamp(raw):
    raw = bytearray(raw)
    raw[0] &= 248
    raw[31] &= 127
    raw[31] |= 64
    return int.from_bytes(bytes(raw), "little")


def publickey_from_seed(seed):
    h = hashlib.sha512(seed).digest()
    a = _clamp(h[:32])
    return _encodepoint(_scalarmult(B, a))


def sign(seed, message):
    h = hashlib.sha512(seed).digest()
    a = _clamp(h[:32])
    A = _encodepoint(_scalarmult(B, a))
    prefix = h[32:]
    r = int.from_bytes(hashlib.sha512(prefix + message).digest(), "little") % L
    R = _encodepoint(_scalarmult(B, r))
    k = int.from_bytes(hashlib.sha512(R + A + message).digest(), "little") % L
    S = (r + k * a) % L
    return R + S.to_bytes(32, "little")


def verify(publickey, message, signature):
    if len(signature) != 64 or len(publickey) != 32:
        return False
    S = int.from_bytes(signature[32:], "little")
    if S >= L or S == 0:
        return False
    try:
        A = _decodepoint(publickey)
        R = _decodepoint(signature[:32])
    except (ValueError, ZeroDivisionError):
        return False
    k = int.from_bytes(hashlib.sha512(signature[:32] + publickey + message).digest(), "little")
    # Fully cofactored check: [8][S]B == [8]R + [8][k]A
    left = _scalarmult(B, 8 * S)
    right = _edwards_add(_scalarmult(R, 8), _scalarmult(A, 8 * k))
    return left == right


# ----------------------------------------------------------------------------
# RFC 8032 §7.1 vectors — TEST 1, TEST 2, TEST 3
# ----------------------------------------------------------------------------
RFC_VECTORS = [
    {
        "seed": "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        "pub": "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        "msg": "",
        "sig": "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
               "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
    },
    {
        "seed": "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
        "pub": "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        "msg": "72",
        "sig": "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69d"
               "a085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
    },
    {
        "seed": "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
        "pub": "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
        "msg": "af82",
        "sig": "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3a"
               "c18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a",
    },
]


def selftest():
    for i, vec in enumerate(RFC_VECTORS, start=1):
        seed = bytes.fromhex(vec["seed"])
        msg = bytes.fromhex(vec["msg"])
        pub = publickey_from_seed(seed)
        assert pub.hex() == vec["pub"], f"TEST {i}: pubkey mismatch: {pub.hex()}"
        sig = sign(seed, msg)
        assert sig.hex() == vec["sig"], f"TEST {i}: signature mismatch: {sig.hex()}"
        assert verify(pub, msg, sig), f"TEST {i}: verify failed on the official signature"
        bad = bytearray(sig)
        bad[0] ^= 1
        assert not verify(pub, msg, bytes(bad)), f"TEST {i}: tampered signature accepted"
    print(f"SELFTEST PASS: {len(RFC_VECTORS)} official RFC 8032 §7.1 vectors (sign+verify+tamper)")
    return True


def load_seed(path):
    with open(path, "r", encoding="utf-8") as handle:
        seed_hex = handle.read().strip()
    seed = bytes.fromhex(seed_hex)
    if len(seed) != 32:
        raise ValueError("seed must be exactly 32 bytes (64 hex chars)")
    return seed


def cmd_pubkey(args):
    seed = load_seed(args.seed_file)
    print(publickey_from_seed(seed).hex())


def cmd_sign(args):
    # The self-test is MANDATORY before every signing — no flag skips it.
    selftest()
    seed = load_seed(args.seed_file)
    with open(args.file, "rb") as handle:
        message = handle.read()
    print(sign(seed, message).hex())


def cmd_sign_catalog(args):
    selftest()
    seed = load_seed(args.seed_file)
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    catalog_path = os.path.join(repo_root, "packages", "manifest.json")
    with open(catalog_path, "r", encoding="utf-8") as handle:
        catalog = json.load(handle)

    pub_hex = publickey_from_seed(seed).hex()
    signed = 0
    for entry in catalog.get("packages", []):
        package_id = entry.get("package_id")
        url = entry.get("download_url", "")
        file_name = url.rsplit("/", 1)[-1]
        file_path = os.path.join(repo_root, "packages", file_name)
        if not os.path.isfile(file_path):
            print(f"FAIL: package file missing for {package_id}: {file_path}", file=sys.stderr)
            sys.exit(1)
        with open(file_path, "rb") as handle:
            message = handle.read()
        entry["signature"] = sign(seed, message).hex()
        entry["pubkey_id"] = "cto-drs-release-1"
        signed += 1
        print(f"signed {package_id} ({file_name}, {len(message)} bytes)")

    with open(catalog_path, "w", encoding="utf-8") as handle:
        json.dump(catalog, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"catalog updated: {signed} entries signed with pubkey {pub_hex[:16]}..")


def cmd_verify(args):
    with open(args.file, "rb") as handle:
        message = handle.read()
    ok = verify(bytes.fromhex(args.pubkey), message, bytes.fromhex(args.sig_hex))
    print("VALID" if ok else "INVALID")
    sys.exit(0 if ok else 1)


def main():
    parser = argparse.ArgumentParser(description="DRS ed25519 package signing tool")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("selftest", help="Run the RFC 8032 §7.1 self-test")

    cmd = sub.add_parser("pubkey", help="Print the public key hex for a seed file")
    cmd.add_argument("--seed-file", required=True)

    cmd = sub.add_parser("sign", help="Sign a file's raw bytes (self-test runs first)")
    cmd.add_argument("file")
    cmd.add_argument("--seed-file", required=True)

    cmd = sub.add_parser("sign-catalog", help="Sign all packages listed in packages/manifest.json")
    cmd.add_argument("--seed-file", required=True)

    cmd = sub.add_parser("verify", help="Verify a signature over a file")
    cmd.add_argument("file")
    cmd.add_argument("sig_hex")
    cmd.add_argument("--pubkey", required=True)

    args = parser.parse_args()
    if args.cmd == "selftest":
        sys.exit(0 if selftest() else 1)
    elif args.cmd == "pubkey":
        cmd_pubkey(args)
    elif args.cmd == "sign":
        cmd_sign(args)
    elif args.cmd == "sign-catalog":
        cmd_sign_catalog(args)
    elif args.cmd == "verify":
        cmd_verify(args)


if __name__ == "__main__":
    main()
