#!/usr/bin/env python3
"""Remplace, dans le manifeste binaire de l'APK, l'attribut android:priority (0x0101001c)
par android:foregroundServiceType (0x01010599). Nécessaire car aapt2 lie ici les ressources
avec un android.jar API 23 qui ne connaît pas cet attribut (API 29)."""
import struct, sys, zipfile, shutil, os

apk = sys.argv[1]
OLD, NEW = 0x0101001c, 0x01010599
with zipfile.ZipFile(apk) as z:
    data = bytearray(z.read('AndroidManifest.xml'))
    entries = [(i, z.read(i.filename)) for i in z.infolist()]
pos = 8  # en-tête du document XML
done = 0
while pos < len(data):
    ctype, hsize, csize = struct.unpack_from('<HHI', data, pos)
    if ctype == 0x0180:  # RES_XML_RESOURCE_MAP_TYPE
        for off in range(pos + hsize, pos + csize, 4):
            if struct.unpack_from('<I', data, off)[0] == OLD:
                struct.pack_into('<I', data, off, NEW)
                done += 1
    pos += csize
if done != 1:
    sys.exit('patch-manifest: attribut attendu une fois, trouvé %d fois' % done)
tmp = apk + '.tmp'
with zipfile.ZipFile(tmp, 'w') as zo:
    for info, content in entries:
        if info.filename == 'AndroidManifest.xml':
            content = bytes(data)
        zo.writestr(info, content)
shutil.move(tmp, apk)
print('manifeste corrigé (foregroundServiceType)')
