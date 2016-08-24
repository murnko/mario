import cutScene
import exportToJava
import time
import numpy as np
from sklearn import svm



def main():
    scene_get, action_get = cutScene.cut_raw_data()
    scene_train = []
    action_train = []
    scene_test = []
    action_test = []
    for i, scene in enumerate(scene_get):
        if np.random.randint(100) < 80:
            scene_train.append(scene)
            action_train.append(action_get[i])
        else:
            scene_test.append(scene)
            action_test.append(action_get[i])

    print(len(scene_train), len(scene_test))
    exportToJava.toJava(zip(action_train, scene_train), zip(action_test, scene_test))


    clf = svm.SVC(cache_size=2000)

    # distribution = np.histogram(action_get,bins=range(32))
    # print(distribution)

    #clf.fit([[0, 0], [1, 1]],[0, 1])
    print('start fitting')
    start_time = time.monotonic()
    clf.fit(scene_train, action_train)
    print("\n" + str(time.monotonic() - start_time))

    fitness = 0
    for index, test in enumerate(scene_test):
        predict = clf.predict(test.reshape(1,-1))
        correct = predict - action_test[index]
        if correct:
            fitness += 1
            print(test.reshape(6,6))
            print(predict)
            print(action_test[index])
    print(1 - fitness/float(len(scene_test)))

main()
