"""Gera o ícone, o banner de TV e a logo do README a partir do mesmo desenho."""
import os

from PIL import Image, ImageDraw, ImageFont

FONTE = "/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf"
AZUL, VIOLETA = (42, 171, 238), (108, 92, 231)
S = 4  # superamostragem para bordas suaves


def gradiente(w, h):
    base = Image.new("RGB", (w, h))
    px = base.load()
    for y in range(h):
        for x in range(w):
            t = (x / w + y / h) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(AZUL, VIOLETA))
    return base


def marca(d, cx, cy, r, cor=(255, 255, 255), furo=None):
    """Balão de conversa com um play dentro."""
    d.rounded_rectangle([cx - r, cy - r * 0.78, cx + r, cy + r * 0.62], radius=r * 0.34, fill=cor)
    d.polygon([(cx - r * 0.55, cy + r * 0.5), (cx - r * 0.62, cy + r * 1.02), (cx - r * 0.05, cy + r * 0.55)], fill=cor)
    t = r * 0.4
    d.polygon([(cx - t * 0.7, cy - 0.08 * r - t), (cx - t * 0.7, cy - 0.08 * r + t), (cx + t, cy - 0.08 * r)], fill=furo)


def icone(lado, arredondar=True):
    n = lado * S
    img = gradiente(n, n).convert("RGBA")
    d = ImageDraw.Draw(img)
    marca(d, n / 2, n / 2, n * 0.3, furo=(74, 132, 234))
    if arredondar:
        mascara = Image.new("L", (n, n), 0)
        ImageDraw.Draw(mascara).rounded_rectangle([0, 0, n, n], radius=n * 0.22, fill=255)
        img.putalpha(mascara)
    return img.resize((lado, lado), Image.LANCZOS)


def banner():
    # 1280x720: a Fire TV não amplia o banner, então ele precisa nascer no tamanho do bloco da tela inicial.
    w, h = 1280 * 2, 720 * 2
    img = gradiente(w, h)
    d = ImageDraw.Draw(img)
    marca(d, w * 0.22, h * 0.5, h * 0.25, furo=(60, 150, 236))
    fonte = ImageFont.truetype(FONTE, int(h * 0.3))
    d.text((w * 0.38, h * 0.5), "TeleTV", font=fonte, fill=(255, 255, 255), anchor="lm")
    return img.resize((1280, 720), Image.LANCZOS)


res = "app/src/main/res"
for nome, lado in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    os.makedirs(f"{res}/mipmap-{nome}", exist_ok=True)
    icone(lado).save(f"{res}/mipmap-{nome}/ic_launcher.png")
os.makedirs(f"{res}/drawable-nodpi", exist_ok=True)
banner().save(f"{res}/drawable-nodpi/banner.png")
icone(512).save("docs/logo.png")
