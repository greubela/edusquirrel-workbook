"""Regenerate independent QR fixtures: pip install qrcode==8.2 qrcodegen==1.8.0.

Test-only oracles; neither encoder is a project/runtime dependency. Force byte mode,
fixed correction strength (no boosting), and include actual format/version bits.
"""
from pathlib import Path
import qrcode
from qrcode.util import QRData, MODE_8BIT_BYTE
from qrcodegen import QrCode, QrSegment

ECC = [qrcode.constants.ERROR_CORRECT_L, qrcode.constants.ERROR_CORRECT_M,
       qrcode.constants.ERROR_CORRECT_Q, qrcode.constants.ERROR_CORRECT_H]
NAYUKI_ECC = [QrCode.Ecc.LOW, QrCode.Ecc.MEDIUM, QrCode.Ecc.QUARTILE, QrCode.Ecc.HIGH]

def checksum(rows):
    value = 0x811c9dc5
    for row in rows:
        for dark in row:
            value = ((value ^ int(dark)) * 16777619) & 0xffffffff
    return value if value < 0x80000000 else value - 0x100000000

def encoded(payload, version, ecc, mask):
    qr = qrcode.QRCode(version=version, error_correction=ECC[ecc], border=0, mask_pattern=mask)
    qr.add_data(QRData(payload, mode=MODE_8BIT_BYTE), optimize=0)
    qr.make(fit=False)
    return qr.get_matrix()

# Every block-table entry, including uneven blocks and versions with alignment/version information.
fixtures = []
for version in range(1, 41):
    for ecc in range(4):
        payload = bytes((i * 149 + version) % 256 for i in range(version * 2 + 1))
        mask = (version + ecc) % 8
        fixtures.append((version, ecc, mask, checksum(encoded(payload, version, ecc, mask))))

# Full-capacity payloads catch padding, 8/16-bit length fields and block interleaving boundaries.
boundaries = []
from qrcode.base import rs_blocks
for version in range(1, 41):
    for ecc in range(4):
        capacity = (sum(b.data_count for b in rs_blocks(version, ECC[ecc])) * 8 - 4 - (8 if version < 10 else 16)) // 8
        payload = bytes((i * 73 + 129) % 256 for i in range(capacity))
        boundaries.append((version, ecc, capacity, checksum(encoded(payload, version, ecc, 7))))

# Automatic masks independently checked with a second encoder (ISO run-history penalties).
auto = []
for text in ['', 'Hello QR!', 'https://evadid.it/', 'Grüße 🐿', 'A' * 200]:
    payload = text.encode('utf-8')
    qr = QrCode.encode_segments([QrSegment.make_eci(26), QrSegment.make_bytes(payload)],
                               QrCode.Ecc.MEDIUM, boostecl=False)
    auto.append((text, qr.get_version(), qr.get_mask(), checksum(
        [[qr.get_module(x, y) for x in range(qr.get_size())] for y in range(qr.get_size())])))

p = Path('modules/core/shared/src/test/scala/it/evadid/workbook/model/qr/QrCodeFixtures.scala')
p.write_text('''package it.evadid.workbook.model.qr

// Generated with tools/dev/generate-qr-fixtures.py; independent test oracles only.
private[qr] object QrCodeFixtures {
  val symbols = Vector(
''' + ',\n'.join(f'    ({v}, {e}, {m}, {h})' for v,e,m,h in fixtures) + '\n  )\n' +
 '  val boundaries = Vector(\n' + ',\n'.join(f'    ({v}, {e}, {c}, {h})' for v,e,c,h in boundaries) + '\n  )\n' +
 '  val automatic = Vector(\n' + ',\n'.join(f'    ({__import__("json").dumps(t, ensure_ascii=False)}, {v}, {m}, {h})' for t,v,m,h in auto) + '\n  )\n}\n')
