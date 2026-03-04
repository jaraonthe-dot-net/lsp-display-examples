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
* **If the file is empty**, some Inlay Hints will be displayed on the first line,
which you can select/click/trigger to fill the file with content that the server
will provide semantic tokens for, showcasing what each different token type (and
modifier) looks like in your editor.

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
