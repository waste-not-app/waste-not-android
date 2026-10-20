"""Idempotently author the draft evidence for the four-method demo policy."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "src/wastenot_ai/data"
prevention_url = "https://www.epa.gov/climate-change/what-you-can-do-about-climate-change-waste"
trash_url = "https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/toothbrushes/"
glass_url = "https://www.cornwall.gov.uk/rubbish-recycling-and-waste/recycling/recycling-items/broken-glass/"
sharp_url = "https://www.wandsworth.gov.uk/rubbish-and-recycling/recycle-and-reuse/waste-and-recycling-a-z/kitchen-knives/"
ceramic_url = "https://www.islington.gov.uk/recycling-and-rubbish/recycling/what-we-pick-up-as-household-rubbish"


def prevention(identifier, food=False):
    return {
        "id": identifier, "method": "REDUCE", "requires": {}, "risk_compatible": True,
        "text": ("Untuk mencegah sisa makanan berikutnya, rencanakan porsi dan beli hanya yang dibutuhkan. "
                 "Saran ini tidak menetapkan keamanan konsumsi atau pembuangan makanan saat ini.") if food else
                ("Untuk mencegah sampah berikutnya, kurangi pembelian yang tidak diperlukan dan pilih "
                 "produk tahan lama atau kemasan lebih sedikit. Ini saran pencegahan, bukan pembuangan benda saat ini."),
        "source_urls": [prevention_url],
        "steps": ["Rencanakan kebutuhan sebelum membeli dan hindari jumlah berlebih."] if food else
                 ["Pada pembelian berikutnya, beli sesuai kebutuhan dan pertimbangkan produk tahan lama atau kemasan lebih sedikit."],
    }


templates = json.loads((root / "guide_templates.json").read_text("utf-8"))
for key, guide in templates.items():
    if key in {"electronics", "sharp", "toothbrush"}:
        continue
    guide["evidence"] = [e for e in guide["evidence"] if e["method"] != "REDUCE"]
    guide["evidence"].append(prevention("prevent-waste-v2", food=key == "food"))
    if key == "food":
        guide["caution"] = "Foto tidak memastikan makanan aman dikonsumsi. REDUCE adalah pencegahan berikutnya; penanganan sisa saat ini mengikuti layanan organik lokal."
templates["toothbrush"] = {
    "source_urls": [trash_url],
    "steps": ["Konfirmasikan sikat gigi plastik manual, tanpa baterai atau komponen listrik.",
              "Periksa program pengumpulan khusus sebelum memilih residu sesuai aturan setempat."],
    "caution": "Referensi Cornwall, Inggris; bukan aturan penerimaan Indonesia. Sikat gigi elektrik/baterai memerlukan penanganan elektronik khusus.",
    "evidence": [prevention("prevent-waste-v2"), {
        "id": "manual-plastic-residue-v2", "method": "TRASH",
        "text": "Sikat gigi plastik manual yang tidak layak dipakai dan tidak memiliki akses daur ulang khusus dapat diarahkan ke residu sesuai aturan lokal. Referensi Cornwall tidak memasukkannya dalam pengumpulan daur ulang biasa.",
        "requires": {"reusable": "NO", "recycling_access": "NO"},
        "source_urls": [trash_url],
        "steps": ["Pastikan ini sikat gigi plastik manual tanpa baterai, bukan limbah medis atau berbahaya.",
                  "Jika tidak ada program daur ulang khusus, gunakan jalur residu sesuai ketentuan petugas setempat."],
    }],
}

def broken_glass(identifier):
    return {
        "id": identifier, "method": "TRASH", "risk_compatible": True,
        "requires": {"category":"GLASS", "integrity":"DAMAGED"},
        "text": "Kaca pecah diarahkan ke Trash sebagai residu dengan pengemasan pelindung agar tidak melukai petugas. Panduan Cornwall menempatkan pecahan kaca pada jalur residu; konfirmasikan aturan penerima setempat.",
        "source_urls": [glass_url],
        "steps": ["Jangan gunakan kembali kaca yang retak atau pecah dan hindari kontak langsung dengan tepi tajam.",
                  "Gunakan pembungkus pelindung atau wadah kokoh dan beri label berisi pecahan kaca; minta bantuan petugas bila tidak aman.",
                  "Serahkan melalui jalur residu sesuai petunjuk pengelola setempat; jangan campur pecahan lepas dengan daur ulang biasa."],
    }


tableware = templates["tableware"]
tableware["evidence"] = [e for e in tableware["evidence"] if not e["id"].endswith("-v3")]
tableware["evidence"].append(broken_glass("broken-glass-trash-v3"))
for state, requirements in [("damaged", {"integrity":"DAMAGED"}), ("unusable", {"integrity":"INTACT","reusable":"NO"})]:
    tableware["evidence"].append({
        "id": f"ceramic-{state}-trash-v3", "method":"TRASH", "risk_compatible":state == "damaged",
        "requires":{"category":"OTHER", **requirements},
        "text":"Keramik yang rusak atau tidak layak digunakan diarahkan ke Trash sesuai jalur residu setempat, bukan dicampur dengan daur ulang botol kaca. Referensi Islington memasukkan peralatan keramik ke sampah rumah tangga.",
        "source_urls":[ceramic_url],
        "steps":["Jangan gunakan kembali peralatan keramik yang rusak atau tidak layak.",
                 "Lindungi bagian tajam dengan pembungkus atau wadah kokoh dan minta petunjuk penyerahan dari petugas setempat."],
    })
templates["electronics"]["evidence"] = [prevention("prevent-waste-v2"), {
    "id":"specialist-electronics-recycle-v3", "method":"RECYCLE", "risk_compatible":True, "requires":{},
    "text":"Perangkat elektronik diarahkan ke Recycle melalui program pengembalian produsen atau pengelola e-waste yang menerima jenis perangkatnya, agar material dapat dipulihkan. Ini bukan izin memasukkannya ke tong daur ulang biasa.",
    "source_urls":templates["electronics"]["source_urls"],
    "steps":["Hubungi produsen atau pengelola e-waste untuk memastikan penerimaan dan prosedur penyerahan.",
             "Lindungi data pribadi bila dapat dilakukan dengan aman; jangan membongkar baterai atau perangkat rusak sendiri.",
             "Serahkan melalui layanan khusus; perangkat dengan baterai tidak dimasukkan ke tong sampah atau daur ulang biasa."],
}]
templates["sharp"].update(source_urls=[sharp_url], evidence=[prevention("prevent-waste-v2"), {
    "id":"knife-specialist-recycle-v3", "method":"RECYCLE", "risk_compatible":True,
    "requires":{"object_key":"knife"},
    "text":"Pisau logam dapat diarahkan ke Recycle melalui pengelola logam yang menerima benda tajam. Referensi Wandsworth menyediakan jalur scrap metal khusus; konfirmasikan penerimaan dan pengemasan kepada petugas lokal.",
    "source_urls":[sharp_url],
    "steps":["Hubungi pengelola logam untuk memastikan pisau diterima dan ikuti prosedur penyerahannya.",
             "Lindungi bilah agar tidak melukai petugas; jangan masukkan pisau lepas ke tong daur ulang biasa."],
}])
templates["sharp"]["evidence"].append({
    "id":"scissors-specialist-recycle-v3", "method":"RECYCLE", "risk_compatible":True,
    "requires":{"object_key":"scissors"},
    "text":"Gunting logam diarahkan ke Recycle melalui pusat pengumpulan yang menerima gunting. Referensi Auckland menyediakan jalur tersebut; periksa penerimaan dan lindungi bagian tajam sesuai petunjuk petugas setempat.",
    "source_urls":["https://www.aucklandcouncil.govt.nz/en/rubbish-recycling/get-rid-unwanted-items/organisation/331.html"],
    "steps":["Hubungi pusat pengumpulan yang menerima gunting logam dan tanyakan prosedur pengemasannya.",
             "Lindungi bilah agar tidak melukai petugas; jangan membuang gunting lepas ke tong daur ulang biasa."],
})

guides = json.loads((root / "guides.json").read_text("utf-8"))
for guide in guides:
    guide["version"] = 3
    guide["evidence"] = [e for e in guide["evidence"] if e["method"] != "REDUCE"]
    guide["evidence"].append(prevention(guide["object_key"] + "-prevent-waste-v2"))
    if guide["object_key"] == "glass_bottle":
        guide["evidence"] = [e for e in guide["evidence"] if e["id"] != "glass-broken-trash-v3"]
        guide["evidence"].append(broken_glass("glass-broken-trash-v3"))
    if guide["object_key"] == "battery":
        guide["evidence"] = [prevention("battery-prevent-waste-v2"), {
            "id":"battery-specialist-recycle-v3", "method":"RECYCLE", "risk_compatible":True, "requires":{},
            "text":"Baterai bekas diarahkan ke Recycle melalui pengumpul baterai atau pengelola limbah khusus yang menerima jenisnya. Bahan berharga dapat dipulihkan; jangan mencampurnya ke sampah rumah tangga atau tong daur ulang biasa.",
            "source_urls":guide["source_urls"],
            "steps":["Hubungi pengumpul baterai atau pengelola limbah khusus untuk memastikan jenis baterai diterima.",
                     "Jika baterai rusak, bocor atau menggembung, minta prosedur penanganan kepada produsen/petugas dan jangan membongkarnya.",
                     "Serahkan sesuai prosedur layanan khusus; jangan dibakar atau dimasukkan ke tong sampah/daur ulang biasa."],
        }]
(root / "guide_templates.json").write_text(json.dumps(templates, ensure_ascii=False, indent=2) + "\n", "utf-8")
(root / "guides.json").write_text(json.dumps(guides, ensure_ascii=False, indent=2) + "\n", "utf-8")
