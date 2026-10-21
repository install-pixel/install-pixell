"""Builds an offline translation package for the keyboard from a Helsinki-NLP OPUS-MT model.

Runs on a computer with internet (or in GitHub Actions); the phone only imports the resulting zip.

    python export_opus_mt.py --model Helsinki-NLP/opus-mt-uk-pl --direction UK_TO_PL \
        --phrases phrases/uk.txt --out build
    # multilingual models need the target language token, e.g. English → Polish:
    python export_opus_mt.py --model Helsinki-NLP/opus-mt-en-zlw --direction EN_TO_PL \
        --target-token ">>pol<<" --phrases phrases/en.txt --out build
    # larger, more accurate "tc-big" models are built as a second tier:
    python export_opus_mt.py --model Helsinki-NLP/opus-mt-tc-big-zle-zlw --direction UK_TO_PL \
        --target-token ">>pol<<" --tier accurate --phrases phrases/uk.txt --out build

Package contents (see mt/src/main/kotlin/.../MarianTranslator.kt):
    model.properties, source.spm, target.spm, vocab.txt,
    encoder_model.onnx, decoder_model.onnx, decoder_with_past_model.onnx  (dynamic int8)
    README.md (model card), tokenizer_check.tsv, reference.tsv  (used by the CI comparison test)
"""
import argparse
import shutil
import time
import zipfile
from pathlib import Path

from huggingface_hub import HfApi, hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from optimum.exporters.onnx import main_export
from optimum.onnxruntime import ORTModelForSeq2SeqLM
from transformers import AutoConfig, MarianTokenizer

ONNX_FILES = ["encoder_model.onnx", "decoder_model.onnx", "decoder_with_past_model.onnx"]


def read_phrases(path: Path) -> list[str]:
    return [l.strip() for l in path.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--direction", required=True,
                    choices=["UK_TO_PL", "PL_TO_UK", "UK_TO_EN", "EN_TO_UK", "PL_TO_EN", "EN_TO_PL"])
    ap.add_argument("--target-token", default="", help="language token of multilingual models, e.g. >>pol<<")
    ap.add_argument("--tier", default="standard", choices=["standard", "accurate"])
    ap.add_argument("--phrases", required=True, type=Path)
    ap.add_argument("--out", required=True, type=Path)
    args = ap.parse_args()

    slug = args.direction.lower().replace("_to_", "-") + ("-accurate" if args.tier == "accurate" else "")
    prefix = args.target_token.strip()
    work = args.out / slug
    fp32, pkg = work / "fp32", work / "pkg"
    shutil.rmtree(work, ignore_errors=True)
    pkg.mkdir(parents=True)

    print(f"Exporting {args.model} to ONNX ...", flush=True)
    main_export(args.model, output=fp32, task="text2text-generation-with-past", no_post_process=True)
    for name in ONNX_FILES:
        print(f"Quantizing {name} ...", flush=True)
        quantize_dynamic(fp32 / name, pkg / name, weight_type=QuantType.QInt8)

    tok = MarianTokenizer.from_pretrained(args.model)
    cfg = AutoConfig.from_pretrained(args.model)
    for name in ["source.spm", "target.spm", "README.md", "config.json", "generation_config.json"]:
        try:
            shutil.copy(hf_hub_download(args.model, name), pkg / name)
        except Exception as e:  # generation_config.json is optional
            print(f"  (skip {name}: {e})")

    vocab = tok.get_vocab()
    if prefix:
        assert prefix in vocab, f"{prefix} is not a token of {args.model}"
    by_id = sorted(vocab.items(), key=lambda kv: kv[1])
    assert [i for _, i in by_id] == list(range(len(by_id))), "vocabulary ids are not contiguous"
    assert all("\n" not in t for t, _ in by_id)
    (pkg / "vocab.txt").write_text("\n".join(t for t, _ in by_id) + "\n", encoding="utf-8")

    info = HfApi().model_info(args.model)
    license_id = (info.card_data or {}).get("license", "") if info.card_data else ""
    props = {
        "direction": args.direction,
        "model": args.model,
        "model_revision": info.sha or "",
        "license": license_id,
        "decoder_start_token_id": cfg.decoder_start_token_id,
        "eos_token_id": cfg.eos_token_id,
        "pad_token_id": cfg.pad_token_id,
        "unk_token_id": tok.unk_token_id,
        "max_length": cfg.max_length or 512,
        "quantization": "dynamic-int8",
        "tier": args.tier,
    }
    if prefix:
        props["source_prefix"] = prefix
    (pkg / "model.properties").write_text("".join(f"{k}={v}\n" for k, v in props.items()), encoding="utf-8")

    phrases = read_phrases(args.phrases)

    def model_input(text: str) -> str:
        # Hugging Face expects the language token at the start of the text; the Kotlin tokenizer adds it
        # from model.properties, so the check files keep the plain phrase.
        return f"{prefix} {text}" if prefix else text

    with open(pkg / "tokenizer_check.tsv", "w", encoding="utf-8") as f:
        for p in phrases:
            f.write(p + "\t" + " ".join(map(str, tok(model_input(p)).input_ids)) + "\n")

    print("Reference translations with the quantized ONNX model (greedy) ...", flush=True)
    ort = ORTModelForSeq2SeqLM.from_pretrained(pkg, use_cache=True, use_merged=False)
    start = time.time()
    with open(pkg / "reference.tsv", "w", encoding="utf-8") as f, open(pkg / "reference_beam.tsv", "w", encoding="utf-8") as fb:
        for p in phrases:
            inputs = tok(model_input(p), return_tensors="pt")
            out = ort.generate(**inputs, num_beams=1, do_sample=False, max_new_tokens=256)
            f.write(p + "\t" + tok.decode(out[0], skip_special_tokens=True) + "\n")
            out = ort.generate(**inputs, num_beams=4, do_sample=False, max_new_tokens=256, length_penalty=1.0)
            fb.write(p + "\t" + tok.decode(out[0], skip_special_tokens=True) + "\n")
    print(f"  {len(phrases)} phrases in {time.time() - start:.1f}s")

    zip_path = args.out / f"offline-keyboard-mt-{slug}.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for file in sorted(pkg.iterdir()):
            z.write(file, file.name)
    sizes = {p.name: round(p.stat().st_size / 1e6, 1) for p in sorted(pkg.iterdir())}
    print(f"Wrote {zip_path} ({zip_path.stat().st_size / 1e6:.1f} MB); files MB: {sizes}; license: {license_id}")


if __name__ == "__main__":
    main()
