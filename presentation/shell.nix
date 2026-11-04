let
  pkgs = import <nixpkgs> { };

  quartoPatched = pkgs.quarto.overrideAttrs (oldAttrs: {
    postPatch = (oldAttrs.postPatch or "") + ''
      substituteInPlace bin/quarto.js \
        --replace-fail "syntax-highlighting" "highlight-style"
    '';
  });
in
pkgs.mkShell {
  packages = [ quartoPatched ];
}
