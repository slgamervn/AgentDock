# AgentDock Update Manifest Protocol v1

**Status:** Implementation foundation. Not yet enabled for production releases.

This document defines the bytes signed by the manifest key. The Android verifier must verify the envelope before parsing the payload, following URLs, or acting on any update metadata.

## 1. Trust model

- The app contains a small, explicit key ring of trusted manifest public keys.
- Each key has a stable `keyId`; a manifest cannot add or replace trusted keys.
- The APK signing key and manifest signing key are independent.
- The manifest signing private key is only available to the protected release job.
- Unknown key IDs, algorithms, schema versions, malformed encodings and invalid signatures are rejected.
- The manifest is untrusted input until verification completes.

## 2. Envelope

The outer JSON object has exactly these fields:

```json
{
  "schemaVersion": 1,
  "keyId": "agentdock-update-2026",
  "algorithm": "ECDSA_P256_SHA256_DER",
  "payload": "BASE64URL_WITHOUT_PADDING",
  "signature": "BASE64URL_WITHOUT_PADDING"
}
```

The envelope parser must reject duplicate keys, unknown keys, missing fields, unexpected types and trailing JSON content. It must enforce a small envelope-size limit before allocating large buffers.

## 3. Signature algorithm and signed bytes

Version 1 supports exactly:

- Curve: NIST P-256 / `secp256r1`.
- Digest: SHA-256.
- Signature: ECDSA ASN.1 DER sequence (the representation used by Java's `SHA256withECDSA` provider).
- Base64: RFC 4648 URL-safe alphabet without padding.

The algorithm identifier is intentionally **not** `ES256` or JWS. JWS ES256 represents ECDSA as fixed-width `R || S`, whereas this protocol uses ASN.1 DER. The formats must never be mixed.

The signed input is the following ASCII byte sequence, with literal LF characters and no trailing newline:

```text
AgentDock-Update-Manifest
1
{keyId}
ECDSA_P256_SHA256_DER
{payload}
```

Here `{payload}` is the exact canonical, unpadded Base64URL value from the envelope. The signature is:

```text
Base64URLWithoutPadding(
  ECDSA_SHA256_Sign(privateKey, signedInputBytes)
)
```

The verifier checks the key ID against the built-in key ring, validates that the selected public key is P-256, reconstructs the signed input exactly, verifies the signature and returns the original payload bytes. It does not normalize or re-serialize payload JSON.

## 4. Payload contract

The payload is UTF-8 JSON. The production parser must reject duplicate keys, unknown fields, missing required fields, invalid types, invalid dates and trailing content. Recommended fields:

| Field | Type | Requirement |
|---|---|---|
| `schemaVersion` | integer | Required, currently 1 |
| `packageName` | string | Required; must equal `com.slg.agentdock` |
| `versionName` | string | Required |
| `versionCode` | integer | Required positive Android version code |
| `channel` | string | Exactly `stable` or `preview` |
| `publishedAt` | RFC 3339 string | Required |
| `issuedAt` | RFC 3339 string | Required |
| `expiresAt` | RFC 3339 string | Required; must be after issuedAt |
| `minimumSupportedVersionCode` | integer | Required; only raised for justified security/support cases |
| `minSdk` | integer | Required; must not exceed device API |
| `targetSdk` | integer | Required |
| `supportedAbis` | array of strings | Required; must include a device-supported ABI |
| `apk.url` | HTTPS URL | Required; host and redirect chain must pass a separately pinned allowlist |
| `apk.sizeBytes` | integer | Required positive size with a hard client limit |
| `apk.sha256` | lowercase hex string | Required, exactly 64 characters |
| `changelog` | object | Required with Added, Improved, Fixed, Security and Compatibility arrays |

The outer signature authenticates the exact payload bytes. The payload parser must not be invoked before signature verification.

## 5. Verifier constraints

The first implementation slice:

- Accepts schema version 1 only.
- Accepts `ECDSA_P256_SHA256_DER` only.
- Accepts key IDs matching `[A-Za-z0-9._-]{1,64}`.
- Accepts payloads from 1 byte through 64 KiB.
- Accepts DER signature byte arrays from 8 through 72 bytes.
- Requires canonical unpadded Base64URL encoding.
- Copies the verified payload before returning it to the caller.

The later parser, checker and downloader must impose their own limits and validation. A valid signature does not prove that the payload is compatible or that its URL is safe.

## 6. Release key setup still required

Before production signing is enabled:

1. Generate a dedicated P-256 manifest key pair in a trusted offline environment.
2. Verify the public key and fingerprint independently.
3. Add only the public key to the app's pinned key ring.
4. Store the private key in a protected GitHub Environment secret.
5. Test signing and verification using a known test vector.
6. Ensure release logs never print private key material or secret values.

Do not reuse or derive this key from the APK JKS. Do not commit the private key, a JKS, a PEM secret or a token.
