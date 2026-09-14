# Report assets

## `helmet.png`

The yellow helmet that identifies the app, embedded in the header of every
generated HTML test report.

**Derived from:** `app/src/test/resources/brand/ic_helmet_emblem_master.png`
(528x528, the same artwork that ships as `ic_helmet_emblem` in five densities).

**Why a committed derivative and not the master?**

Two reasons, both about the report rather than about the picture:

1. **Determinism.** The report must be byte-identical when regenerated from the
   same inputs. Scaling the master at generate time would make the output
   depend on which Pillow and zlib happen to be installed on the machine, so
   the same run could produce two different files. Scaling once and committing
   the result removes that variable, and removes Pillow from the generator's
   runtime dependencies entirely — it only base64-encodes bytes.

2. **Size.** The master is 257 KB, which is 343 KB once base64-encoded, on a
   report of roughly 200 KB. Tripling the file for decoration is a bad trade.
   At 144x144 and a 64-colour palette this is 3.2 KB, about 1.5% of the report.

**To regenerate** (only needed if the brand artwork itself changes):

```bash
python3 - <<'EOF'
from PIL import Image
src = Image.open('app/src/test/resources/brand/ic_helmet_emblem_master.png').convert('RGBA')
assert src.width == src.height, f"master must be square, got {src.size}"
im = src.resize((144, 144), Image.LANCZOS).quantize(colors=64, method=Image.FASTOCTREE)
im.save('05_tests/infra/assets/helmet.png', optimize=True)
EOF
```

**Keep it square.** Incident #11 was this exact artwork rendered deformed by a
non-uniform scale. `test_generate_html_report.py` asserts the asset is square
and that the header sets explicit equal width and height, so the report cannot
repeat that defect silently.

The report degrades to a plain text header if this file is missing. A test
report must never fail to generate over an ornament.
