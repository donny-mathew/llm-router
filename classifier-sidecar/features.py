import re
from typing import Any

INSTRUCTION_KEYWORDS = {"analyze", "compare", "explain", "implement", "debug", "refactor",
                        "evaluate", "synthesize", "critique", "design", "architect", "optimize"}

MULTI_STEP_KEYWORDS = {"step 1", "first", "then", "finally", "also", "next", "additionally",
                       "furthermore", "lastly", "subsequently"}

MATH_SYMBOLS = set("=∑∫∂∇∆±×÷√∞∈∉⊂⊃∪∩")


def extract_features(messages: list[dict[str, Any]], max_tokens: int | None, temperature: float | None) -> list[float]:
    all_text = ""
    system_present = 0
    message_count = len(messages)

    for msg in messages:
        content = msg.get("content", "")
        if isinstance(content, list):
            content = " ".join(
                part.get("text", "") for part in content if isinstance(part, dict) and part.get("type") == "text"
            )
        all_text += content + " "
        if msg.get("role") == "system":
            system_present = 1

    total_chars = len(all_text)
    avg_message_length = total_chars / message_count if message_count else 0

    lower = all_text.lower()
    code_block_count = len(re.findall(r"```", all_text)) // 2
    question_count = all_text.count("?")
    instruction_depth = sum(1 for kw in INSTRUCTION_KEYWORDS if kw in lower)
    multi_step_indicators = sum(1 for kw in MULTI_STEP_KEYWORDS if kw in lower)
    math_symbol_count = sum(1 for ch in all_text if ch in MATH_SYMBOLS)

    max_tokens_norm = min(1.0, (max_tokens or 0) / 4096)
    temp_value = temperature if temperature is not None else 0.0

    return [
        min(1.0, total_chars / 4000),   # normalized
        min(1.0, message_count / 20),
        min(1.0, avg_message_length / 500),
        float(system_present),
        min(1.0, code_block_count / 5),
        min(1.0, question_count / 10),
        min(1.0, instruction_depth / len(INSTRUCTION_KEYWORDS)),
        min(1.0, multi_step_indicators / len(MULTI_STEP_KEYWORDS)),
        min(1.0, math_symbol_count / 10),
        max_tokens_norm,
        temp_value,
    ]


FEATURE_NAMES = [
    "total_chars_norm",
    "message_count_norm",
    "avg_message_length_norm",
    "system_prompt_present",
    "code_block_count_norm",
    "question_count_norm",
    "instruction_depth_norm",
    "multi_step_indicators_norm",
    "math_symbols_norm",
    "max_tokens_norm",
    "temperature",
]
