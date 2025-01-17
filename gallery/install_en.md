# Installation
BackSnap is intentionally easy to install.
## 1. Install `JAVA`
This varies greatly depending on the distribution used. Please refer to your **distribution guide** on how to install **java** so you do it right from the start. I recommend using `java openjdk 17 or 20` but version 17 and any above should work fine.
#### For manjaro or arch:
`pamac install jdk-openjdk`

or
`pacman -S jdk-openjdk`

or
`trizen -S jdk-openjdk`
#### Test as `user` and as `root`:
`java-version`
```
openjdk version "19.0.2" 2023-01-17
OpenJDK Runtime Environment (build 19.0.2+7)
OpenJDK 64-Bit Server VM (build 19.0.2+7, mixed mode)
```
See also: [archlinux wiki java](https://wiki.archlinux.org/title/java) , [manjaro pamac](https://wiki.manjaro.org/index.php/Pamac) , [manjaro pacman] (https://wiki.manjaro.org/index.php/Pacman_Overview) , [manjaro trizen](https://wiki.archlinux.de/title/Trizen)

## 2. Install `pv`
`pv` shows the progress and speed of the snapshot transfer. It is not required but recommended.
#### for ARCH or manjaro:
`pamac install pv`

or
`pacman -S pv`

or
`tricen -S pv`

## 3. Install `BackSnap`
The installation must be done as root (or with sudo). It should be done in such a way that BackSnap is accessible in both **root**'s `$PATH` and **user**'s `$PATH`.

`echo $PATH`
#### in /usr/local/bin
If `/usr/local/bin` is in your path, it's easiest to install BackSnap there.

`sudo wget https://github.com/andreaskielkopf/BackSnap/raw/master/backsnap -O /usr/local/bin/backsnap`

Make BackSnap executable

`sudo chmod a+x /usr/local/bin/backsnap`
#### or in /usr/bin
If `/usr/local/bin` is not in your path, the easiest way is to install BackSnap in `/usr/bin`.

`sudo wget https://github.com/andreaskielkopf/BackSnap/raw/master/backsnap -O /usr/bin/backsnap`

Make BackSnap executable

`sudo chmod a+x /usr/bin/backsnap`
### Test as `user` and as `root`:
`backsnap -x`
```
args > -x
<html>BackSnap <br> Version 0.5.1 <br> (2023/04/22)
```
----
Saturday, June 03, 2023 06:22