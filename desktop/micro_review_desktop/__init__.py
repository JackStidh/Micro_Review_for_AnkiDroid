"""Micro Review Desktop, an Anki add-on: a few cards every so often while you use your PC, like Micro Review Focus on
the phone, which it pauses while the PC is in use. See config.md.
"""
try:
    from aqt import mw
except Exception:  # Not inside Anki (tests).
    mw = None

if mw is not None:
    from .controller import setup

    setup(mw)
