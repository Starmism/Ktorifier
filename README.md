# Ktorifier

Ktorifier is a standalone project that implements both V1 and V2 of the Votifier Protocol.

It is mostly a fun project I used to learn both Ktor and sockets in general, but I also plan on using it
on the Minecraft network I run.

Right now it just prints votes it receives to the logger, but soon it will forward the votes to an HTTP server as well.

# Usage

Compile it with `./gradlew shadowJar`.

On first run, it will generate a `public.key` and a `private.key` in the same directory as the jar.

You should also create a file called `config.json` that follows the format of [the config file](src/main/resources/config.json).

# Acknowledgements

I used the source of [NuVotifier](https://github.com/NuVotifier/NuVotifier) for a great deal of inspiration and also tried
to use similar messaging on the V2 protocol to be as compatible as possible with clients.

Credit is also owed to the creator of the original [Votifier](https://github.com/vexsoftware/votifier), as that is what
both NuVotifier and this project are based on originally.

# License

Ktorifier is GNU GPLv3 licensed. This project's license can be viewed [here](LICENSE).