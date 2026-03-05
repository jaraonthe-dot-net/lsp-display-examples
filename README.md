# LSP Display Examples

This is a language server implementation that's intended to showcase what
various LSP features look like in a client.

For now, you can check out what different Semantic Token types and modifiers
look like.

## How to use

* After starting the server, a client can communicate with it either via stdio
(when starting it as a subprocess) or via a TCP port (given with the `--port`
option). The latter gives you quite verbose log output.
* You have to provide the client/IDE/editor yourself.
* Then, in your client, open a file for which it utilizes this language server
(this is often triggered by the file extension).
* **If the first line is empty**, some Inlay Hints and/or Code Actions will be
displayed on the first line, which you can select/click/trigger to fill the file
with content that the server will provide semantic tokens for, showcasing what
each different token type (and modifier) looks like in your editor.
* Hover information describes which token type and modifiers are used at this
location.

### Syntax

This syntax is used by the generator Hints / Code Actions mentioned above. But
you can also use it manually, if you wish.

The semantic tokens are attached to keywords with the same name. E.g. `namespace`
will be marked with that semantic token type.

To include modifiers, put them after the token type keyword, separated by ':'.
E.g. `type:readonly` or `class:abstract:defaultLibrary` (with more than one modifier).
A shorthand is to use a type keyword followed by ':' and then whitespace - after
that all modifier keywords used on their own will be combined with that type.
E.g. `function: declaration static`.

As all modifiers can be combined, and there are currently 10 possible modifiers
in LSP, there are 1024 possible combinations. In order to show them all, the
following compact syntax is available: Token type followed by '|'. After that,
each following character until the end of the line is marked with a different
modifier combination. E.g. `operator|abcdefghsdfsdvawer...`.
To know which modifier is used you may use hover.

## System Requirements

* Java 25 or higher

## About

Created by Jakob Rathbauer in Spring 2026.

Based on my own work in the [OpenVADL](https://github.com/OpenVADL/openvadl/) project.

## License

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <[http://www.gnu.org/licenses/](http://www.gnu.org/licenses/)>.
