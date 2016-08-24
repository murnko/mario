import numpy as np

cut_actions_result = []
cut_scenes_result = []

def append_cut(scene, action):
    cut_scenes_result.append(scene)
    cut_actions_result.append(action)

def cut_raw_data():
    line_counter = 0
    num_lines = sum(1 for line in open("raw_dataA3"))

    print(num_lines)
    with open("raw_dataA3", "r") as input:
        scene_rows = []
        while line_counter <= num_lines:
                line = input.readline()

                if line_counter % 23 == 22:
                    # print(scene_rows)
                    split_line = (line.split(" "))[0:-1]
                    new_action_bitlist = np.array(split_line).astype(int)

                    out_action = 0
                    for bit in new_action_bitlist:
                        out_action = (out_action << 1) | bit
                    if out_action == 0:
                        scene_rows.clear()
                    else:
                        new_scene = np.row_stack(scene_rows[8:-8]).astype(int).reshape(1,-1)[0]
                        scene_rows.clear()
                        # print(new_big_scene)
                        append_cut(new_scene, out_action)
                else:
                    split_line = (line.split(" "))[11:-6]
                    scene_rows.append(split_line)
                line_counter += 1
    print(len(cut_actions_result))

    input.close()
    return cut_scenes_result, cut_actions_result

