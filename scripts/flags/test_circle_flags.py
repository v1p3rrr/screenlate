import os
import sys
import unittest
import xml.etree.ElementTree as ElementTree

sys.path.insert(0, os.path.dirname(__file__))
import circle_flags  # noqa: E402

ANDROID = "{http://schemas.android.com/apk/res/android}"


def svg(*elements, mask='<circle cx="256" cy="256" r="256" fill="#fff"/>'):
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'
        f'<mask id="a">{mask}</mask><g mask="url(#a)">{"".join(elements)}</g></svg>'
    )


class CircleFlagsTest(unittest.TestCase):
    def test_paths_circles_and_rects_become_paths_in_order(self):
        text = svg(
            '<path fill="#eee" d="M0 0h512v512H0z"/>',
            '<circle cx="256" cy="256" r="111.3" fill="#D80027"/>',
            '<rect width="34" height="22" x="239" y="299" fill="#ff9811" rx="11" ry="11"/>',
        )
        shapes = circle_flags.shapes(text)
        self.assertEqual(shapes[0], ("M0 0h512v512H0z", "#eeeeee"))
        self.assertEqual(shapes[1], ("M144.7,256a111.3,111.3 0 1,0 222.6,0a111.3,111.3 0 1,0 -222.6,0z", "#d80027"))
        self.assertEqual(shapes[2][1], "#ff9811")
        self.assertTrue(shapes[2][0].startswith("M250,299h12a11,11 0 0,1 11,11"))

    def test_the_drawable_is_clipped_to_the_circle(self):
        drawable = circle_flags.vector_drawable(svg('<path fill="#0052b4" d="M0 0h512v512H0z"/>'), "xx")
        root = ElementTree.fromstring(drawable.split("\n", 1)[1])
        self.assertEqual(root.get(ANDROID + "viewportWidth"), "512")
        children = list(root)
        self.assertEqual(children[0].tag, "clip-path")
        self.assertEqual(children[1].get(ANDROID + "fillColor"), "#0052b4")
        self.assertIn("xx.svg", drawable)

    def test_anything_else_is_refused(self):
        for text in (
            svg('<path fill="url(#b)" d="M0 0h1v1z"/>'),
            svg('<polygon fill="#fff" points="0,0 1,1 0,1"/>'),
            svg('<path fill="#fff" d="M0 0h1v1z" opacity=".5"/>'),
            svg('<path fill="#fff" d="M0 0h1v1z"/>', mask='<circle cx="256" cy="256" r="200" fill="#fff"/>'),
        ):
            with self.assertRaises(circle_flags.UnsupportedSvg):
                circle_flags.shapes(text)

    def test_resource_names_take_underscores(self):
        self.assertEqual(circle_flags.resource_name("tz"), "flag_tz")
        self.assertEqual(circle_flags.resource_name("lang_la"), "flag_lang_la")
        self.assertEqual(circle_flags.resource_name("gb-wls"), "flag_gb_wls")

    def test_numbers_are_short(self):
        self.assertEqual(circle_flags.number("256.0"), "256")
        self.assertEqual(circle_flags.number(-0.0), "0")
        self.assertEqual(circle_flags.number(1 / 3), "0.333")


if __name__ == "__main__":
    unittest.main()
