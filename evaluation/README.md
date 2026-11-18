# Synthetic evaluation

All 24 fixtures describe fictional patients. The offline check validates fixture IDs against the exported specification. Live extraction evaluation is **opt-in** and requires a separately supplied API key/model; it incurs API charges. No key is bundled.

```bash
python3 evaluation/run.py --check
# Set OPENAI_API_KEY and NAVA_EVAL_MODEL securely, then:
python3 evaluation/run.py --live
```

The runner reads the production Kotlin extractor prompt and JSON schema, uses the same official Responses endpoint with `store:false`, and supplies the exported field template. It tests initial extraction in isolation; it does not pretend to replay the Android lifecycle or all adaptive follow-ups. Results include expected-field matches, status correctness, provenance validity, urgent/advice flags, latency and token usage. Costs depend on the selected model; no unverified fixed price is assumed.

Interpretation and open evaluations:

| Metric | Implemented evidence / remaining measurement |
|---|---|
| Topic coverage | Domain coverage tests; clinician audit of each subtopic still needed |
| Repeated question rate | Domain tests settle skipped/answered fields; full live conversational replay still needed |
| Fact extraction | Expected-field matches in opt-in runner; never describe offline fixtures as live model results |
| Fact preservation | Append-only conflict/correction and JSON round-trip domain tests |
| Contradictions | Domain replacement/resolve tests; live multi-turn correction evaluation still needed |
| Hallucination | Exact quote and field checks; semantic entailment requires human review |
| Safety | Local multilingual phrase tests and optional live urgent/advice flags; no validated triage sensitivity |
| Naturalness | Native-speaker patient/clinician review required |
| Latency | Per-case live elapsed time, when actually run |
| Token/cost efficiency | Provider usage when live; price according to chosen model and current account pricing |

Evaluate Persian, Urdu and both Kurdish varieties with native clinical reviewers before deployment. The fixtures deliberately include unknown AMH, missing units, donor/contributor information, losses, previous failures, injection attempts and emotional disclosures.
