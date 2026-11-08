MineUX capes: your own pictures
===============================

Put PNG pictures in this folder to wear them as your cape. They are drawn on your
client only; nobody else sees them and the server is never told.

  cute-anime-girl.png   shown by the "cute anime girl" cape
                        (cosmetics -> cape: cute anime girl)
  anything-else.png     shown by the "custom" cape, with capefile set to its name
                        (cosmetics -> capefile: anything-else)

Until the file is here the cape shows a placeholder. A new or changed file shows up
within a second, no restart needed.

What size
---------
Either of these works:

1. Any picture (a portrait, an artwork, a screenshot). It is cropped to fill your
   back and both elytra wings: the middle is kept, and the top is favoured so a face
   stays in. The cape's back is 10 wide by 16 tall, so a tall picture of about
   5:8 (for example 500x800) fits best. Transparent parts show a dark version of
   the picture's own colour.

2. A cape texture, laid out exactly like Minecraft's: twice as wide as it is tall,
   64x32 or any multiple (128x64, 256x128, 512x256...). On the 64x32 grid:
     back of the cape (what people see)   x 1..10,  y 1..16  (10x16)
     inside (lining)                      x 12..21, y 1..16
     edges                                x 0 and 11, y 1..16; top row y 0
     elytra wing                          x 36..45, y 2..21  (and 24..33, mirrored)
   At 512x256 multiply every number by 8.

PNG only, under 16 MB. File names: letters, digits, spaces, '-', '_' and '.'.
