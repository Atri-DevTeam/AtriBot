import unittest
from build_pool import flatten, make_catalog, compatible, refresh_display_names, display_name
import copy


class PoolTests(unittest.TestCase):
    def test_door_names_distinguish_source_and_direction(self):
        lang = {'block.minecraft.bamboo_door': '竹门'}
        self.assertEqual(display_name('block.bamboo_wood_door.open', '门：嘎吱作响', lang), '竹门：打开')
        self.assertEqual(display_name('block.bamboo_wood_door.close', '门：嘎吱作响', lang), '竹门：关闭')
        self.assertEqual(display_name('block.nether_wood_trapdoor.open', '活板门：打开', lang), '下界木质活板门：打开')

    def test_refresh_names_preserves_audio_candidates_and_original_label(self):
        event = {"id": "entity.parrot.imitate.warden", "name": "鹦鹉：呻吟",
                 "variants": [{"path": "original.ogg", "playbackPath": "playback.ogg"}],
                 "fingerprints": ["original-hash"], "playable": True}
        group = {"anchor": event["id"], "name": event["name"], "events": [event["id"], "other"], "difficulty": 3}
        catalog = {"events": [copy.deepcopy(event)], "groups": [copy.deepcopy(group)]}
        self.assertEqual(refresh_display_names(catalog), 1)
        updated = catalog["events"][0]
        self.assertEqual(updated["name"], "鹦鹉：模仿监守者叫声")
        self.assertEqual(updated["originalName"], event["name"])
        self.assertEqual(updated["variants"], event["variants"])
        self.assertEqual(updated["fingerprints"], event["fingerprints"])
        self.assertEqual(catalog["groups"][0], {**group, "name": updated["name"]})
        once = copy.deepcopy(catalog)
        self.assertEqual(refresh_display_names(catalog), 0)
        self.assertEqual(catalog, once)

    def test_nested_references_preserve_pitch_volume_weight(self):
        events = {"a": {"sounds": [{"name": "b", "type": "event", "pitch": 2, "volume": 0.5, "weight": 3}]},
                  "b": {"sounds": [{"name": "x", "pitch": 0.5, "weight": 2}]}}
        self.assertEqual(flatten(events, "a"), [{"asset": "minecraft/sounds/x.ogg", "pitch": 1,
                                               "volume": 0.5, "weight": 6}])

    def test_reference_cycle_fails(self):
        with self.assertRaises(ValueError):
            flatten({"a": {"sounds": [{"name": "a", "type": "event"}]}}, "a")

    def test_catalog_retains_missing_subtitles_empty_events_and_pitch_variants(self):
        sounds = {"empty": {"sounds": []}, "music.menu": {"sounds": ["menu"]},
                  "entity.cow.hurt": {"sounds": [{"name": "cow", "pitch": 0.8}, {"name": "cow", "pitch": 1.2}]}}
        assets = {"minecraft/sounds/menu.ogg": {"hash": "1" * 40}, "minecraft/sounds/cow.ogg": {"hash": "2" * 40}}
        catalog, _ = make_catalog(sounds, {}, assets, "minecraft/26.3-rc-3")
        self.assertEqual(len(catalog["events"]), 3)
        by_id = {e["id"]: e for e in catalog["events"]}
        self.assertFalse(by_id["empty"]["playable"])
        self.assertTrue(by_id["music.menu"]["playable"])
        self.assertEqual(len(by_id["entity.cow.hurt"]["variants"]), 2)

    def test_different_file_names_cannot_hide_equal_audio(self):
        self.assertFalse(compatible([{"name": "甲", "fingerprints": ["pcm:equal"]},
                                     {"name": "乙", "fingerprints": ["pcm:equal"]}]))


if __name__ == "__main__":
    unittest.main()
