# Video mining sketches

Source of the design canvas with rough sketches of the video mining concepts (experiment E10 in
`../../experiments.md`, concepts in `../../notes/video-mining-concepts.md`). Nothing here is decided.

- Live canvas, private to the owner: https://claude.ai/artifact/RYg5awh22DsEwwjym1gpne (read or update it with the
  Artifact tool). This folder is a copy of its version 1791823212-4d6f, kept so the sketches survive if the canvas is
  lost.
- Board texts are in Russian: the canvas is for the owner.
- The files are in the canvas format of the claude.ai "Design" artifact type (`.dc.html` components and the
  `canvas.json` index); they need that runtime and do not render as plain HTML. `Scene.dc.html` is the shared scene
  (landscape video, subtitle, bubble, popup) that every other board imports with props.
- To recreate the canvas: start a new artifact from the Design type and publish these files under `project/`
  (`canvas.json` first, then `Main.dc.html` with `Scene.dc.html`, then the rest).
