"""
The format of training and testing data file is:

<label> <index1>:<value1> <index2>:<value2> ...
.
.
.
"""
def patternWriter(data, fname):
    with open("java_" + fname+".txt", "a") as output:
        for pairTr in data:
            new_row_tr = str(pairTr[0]) + " "
            for x in range(36):
                new_row_tr += str(x) + ":" + str(pairTr[1][x]) + " "
            output.write(new_row_tr+"\n")
        output.close()

def toJava(trainData, testData): # different sizes !
    patternWriter(trainData, 'trainA3')
    patternWriter(testData, 'testA3')
    exit()  # just do it


