{
    description = "Cross-platform http4s proxy tests";

    inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    };

    outputs = {nixpkgs, ...}: let
        mkEnvironment = pkgs: let
            inherit (pkgs) lib;
            jdk = pkgs.jdk21;
            nativeLibraries =
                [
                    pkgs.openssl
                    pkgs.s2n-tls
                    pkgs.boehmgc
                    pkgs.zlib
                ]
                ++ lib.optionals pkgs.stdenv.hostPlatform.isLinux [pkgs.libunwind];
            nativeLibraryPath = lib.makeLibraryPath nativeLibraries;
        in {
            tools =
                [
                    jdk
                    pkgs.sbt
                    pkgs.nodejs_22
                    pkgs.go
                    pkgs.llvmPackages.clang
                    pkgs.pkg-config
                ]
                ++ nativeLibraries;

            nativeEnvironment = ''
                export LIBRARY_PATH=${nativeLibraryPath}:''${LIBRARY_PATH:-}
                export LD_LIBRARY_PATH=${nativeLibraryPath}:''${LD_LIBRARY_PATH:-}
                export DYLD_LIBRARY_PATH=${nativeLibraryPath}:''${DYLD_LIBRARY_PATH:-}
            '';
        };

        systems = [
            "x86_64-linux"
            "aarch64-linux"
            "x86_64-darwin"
            "aarch64-darwin"
        ];
        forAllSystems = nixpkgs.lib.genAttrs systems;
    in {
        devShells = forAllSystems (
            system: let
                pkgs = import nixpkgs {inherit system;};
                environment = mkEnvironment pkgs;
            in {
                default = pkgs.mkShell {
                    packages = environment.tools;
                    shellHook = environment.nativeEnvironment;
                };
            }
        );

        apps = forAllSystems (
            system: let
                pkgs = import nixpkgs {inherit system;};
                environment = mkEnvironment pkgs;
                test = pkgs.writeShellApplication {
                    name = "http4s-proxy-test";
                    runtimeInputs = environment.tools;
                    text = ''
                        ${environment.nativeEnvironment}
                        exec bash ./test.sh "$@"
                    '';
                };
            in {
                test = {
                    type = "app";
                    program = "${test}/bin/http4s-proxy-test";
                };
            }
        );
    };
}

