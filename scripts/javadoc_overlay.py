"""Keeps hand-written Javadoc in generated Java files.

The generators produce code; the Javadoc of the generated public API is written in the Java files themselves.
overlay(generated, existing) returns the generated source with each declaration's Javadoc block taken from the
existing file when the same declaration exists there (matched by its text up to the first '{', ';' or '=',
whitespace-normalized, and by occurrence). Declarations the existing file does not have keep the generator's
Javadoc, if any.
"""
import os
import re

_DECL_END = re.compile(r'[{;=]')


def _blocks(lines):
    """Yields (doc_start, doc_end, decl_start, key) for every Javadoc block followed by a declaration."""
    i, n = 0, len(lines)
    while i < n:
        if lines[i].lstrip().startswith('/**'):
            start = i
            while '*/' not in lines[i]:
                i += 1
            end = i + 1
            j = end
            while j < n and lines[j].strip().startswith('@') and not lines[j].strip().startswith('@interface'):
                j += 1  # annotations between the comment and the declaration
            decl = []
            k = j
            while k < n:
                m = _DECL_END.search(lines[k])
                decl.append(lines[k][:m.start()] if m else lines[k])
                if m:
                    break
                k += 1
            yield start, end, j, ' '.join(' '.join(decl).split())
            i = end
        else:
            i += 1


def _decl_keys(lines):
    """Maps line index -> key for every line that starts a member or type declaration."""
    keys = {}
    decl = re.compile(r'^\s*(public|protected|private|static|final|abstract|default|synchronized|native|'
                      r'class|interface|enum|record)\b')
    for i, line in enumerate(lines):
        if decl.match(line) and not line.strip().startswith(('return', 'case')):
            part, k = [], i
            while k < len(lines):
                m = _DECL_END.search(lines[k])
                part.append(lines[k][:m.start()] if m else lines[k])
                if m:
                    break
                k += 1
            keys[i] = ' '.join(' '.join(part).split())
    return keys


def overlay(generated, existing_path):
    if not os.path.exists(existing_path):
        return generated
    old = open(existing_path, encoding='utf-8').read().split('\n')
    docs, seen = {}, {}
    for s, e, _, key in _blocks(old):
        seen[key] = seen.get(key, 0) + 1
        docs[(key, seen[key])] = old[s:e]
    lines = generated.split('\n')
    # drop the generator's own Javadoc where the existing file supplies one
    gen_doc = {d: (s, e) for s, e, d, _ in _blocks(lines)}
    out, skip, count = [], set(), {}
    keys = _decl_keys(lines)
    for i in sorted(keys):
        key = keys[i]
        count[key] = count.get(key, 0) + 1
        if (key, count[key]) in docs and i in gen_doc:
            skip.update(range(*gen_doc[i]))
    count = {}
    insert = {}
    for i in sorted(keys):
        key = keys[i]
        count[key] = count.get(key, 0) + 1
        doc = docs.get((key, count[key]))
        if doc is not None:
            j = i
            while j > 0 and lines[j - 1].strip().startswith('@') and (j - 1) not in skip:
                j -= 1  # put the comment above the annotations
            insert[j] = doc
    for i, line in enumerate(lines):
        if i in insert:
            out.extend(insert[i])
        if i not in skip:
            out.append(line)
    return '\n'.join(out)
