import argparse
import hashlib
import json
import os

import matplotlib
import numpy as np

from src.main.python.tensorflow.feature import combined_to_features

matplotlib.use("Qt5Agg")
import matplotlib.pyplot as plt
from src.main.python.tensorflow.read_session import combine_data, \
  filter_by_number_skeletons
from src.main.python.tensorflow.nn import Regressor
from srcgen.session_pb2 import Session


def calc_distance(actual, predicted):
  diff = np.absolute(actual - predicted)
  squared = diff * diff
  summed = np.sum(squared, axis=1)
  sqrt = np.sqrt(summed)
  distance = np.mean(sqrt)
  distances = np.mean(diff, axis=0)

  return distance, distances, sqrt


def graph_point_distribution(labels, save_file=None):
  # Plot point distribution
  dist_figure = plt.figure(num=None, figsize=(16, 9), dpi=300)
  plt.ylabel("Y (m)")
  plt.xlabel("X (m)")
  plt.scatter(labels[:, 0], labels[:, 1])
  if save_file is not None:
    dist_figure.savefig(save_file)
  else:
    plt.show()


def unison_shuffled_copies(a, b):
  assert len(a) == len(b)
  p = np.random.permutation(len(a))
  return a[p], b[p]


def get_bounds(labels):
  mins = np.amin(labels, axis=0)
  maxs = np.amax(labels, axis=0)
  return mins[0], maxs[0], mins[1], maxs[1]


def np_append(dest, to_append):
  if dest is None:
    return to_append
  elif len(to_append) > 0:
    return np.concatenate([dest, to_append])
  return dest


def combine_clips(clips, average_size, sensor_raw, background):
  all_labels = None
  all_data = None
  for clip in clips:
    labels, data = combined_to_features(clip,
                                        average_size=average_size,
                                        sensor_raw=sensor_raw,
                                        background=background)
    all_labels = np_append(all_labels, labels)
    all_data = np_append(all_data, data)

  # Tensorflow works on float32s
  all_labels = all_labels.astype(np.float32)
  all_data = all_data.astype(np.float32)

  return all_labels, all_data


def train_model(model_name, model_dir, session_dir, session_ids, num_sensors,
    num_epochs):
  recording_blacklist = [

  ]

  average_size = 0
  sensor_raw = True
  background_sub = True
  all_readings_must_change = True
  hidden_layers = [100, 100, 100]

  hash_string = ""
  for session_id in session_ids:
    hash_string += ":" + session_id
  hash_string += str(num_sensors)
  hash_string += str(average_size)
  hash_string += str(sensor_raw)
  hash_string += str(background_sub)
  hash_string += str(all_readings_must_change)
  hash_string += str(hidden_layers)
  hash_string += str(num_epochs)
  cache_key = hashlib.sha256(hash_string.encode("utf8")).hexdigest()[0:6]

  model_name += "_" + cache_key
  print("Name", model_name)

  all_labels = None
  all_data = None
  for session_id in session_ids:
    root_dir = os.path.join(session_dir, str(session_id))
    session_file = str(session_id) + ".session"
    session = Session()
    with open(os.path.join(root_dir, session_file), 'rb') as f:
      buffer = f.read()
      session.ParseFromString(buffer)

    # Load and combine data
    combined = []
    background_data = []
    for recording in session.recordings:
      if recording.name not in recording_blacklist and recording.id not in recording_blacklist:
        print(
            "Loading {} {} {}".format(session.id, recording.name,
                                      recording.id))
        recording_dir = os.path.join(root_dir, str(recording.id))
        combined_data = combine_data(recording_dir,
                                     num_sensors=num_sensors,
                                     all_sensors_must_change=all_readings_must_change)

        # Check if recording has "background" in name, if so it is a background
        if "background" in recording.name:
          background_data += combined_data
        else:
          combined += combined_data
      else:
        print("Skipped recording {} {} {}".format(session.id, recording.name,
                                                  recording.id))

    print("Filtering by number of occupants...")
    clipped = filter_by_number_skeletons(combined)

    print("Forming data matrices...")
    background = None
    if background_sub:
      # Calculate background
      background_clips = filter_by_number_skeletons(background_data)[0]
      background_labels, background_data = combine_clips(background_clips, 0,
                                                         sensor_raw, None)
      background = np.mean(background_data, axis=0)

      if background_sub and len(background_data) == 0:
        print("Error, no background data")

    # Combine all clips with 1 person into giant matrices
    single_person_clips = clipped[1]
    session_labels, session_data = combine_clips(single_person_clips,
                                                 average_size, sensor_raw,
                                                 background)

    all_labels = np_append(all_labels, session_labels)
    all_data = np_append(all_data, session_data)

  # Shuffle
  # all_labels, all_data = unison_shuffled_copies(all_labels, all_data)

  print("Data points count: ", len(all_labels))

  print("Bounds: ", get_bounds(all_labels))

  # Split data in half
  middle = int(len(all_data) / 2)
  train_data = all_data[:middle]
  test_data = all_data[middle:]
  train_labels = all_labels[:middle]
  test_labels = all_labels[middle:]

  save_model_file = os.path.join(model_dir, model_name + "_model.pb")

  regressor = Regressor(len(all_data[0]), len(all_labels[0]), hidden_layers)

  print("Training model...")
  accuracy, predictions = regressor.train(train_labels, train_data, test_labels,
                                          test_data,
                                          epochs=num_epochs,
                                          save_model=save_model_file)
  distance, distances, indv_distances = calc_distance(test_labels, predictions)
  print(accuracy, distances)

  stats_file = os.path.join(model_dir, model_name + "_stats.txt")
  with open(stats_file, 'w') as file:
    stats = {
      'accuracy': str(accuracy),
      'distance': str(distance),
      'distances': str(distances),
      'num_points': str(len(all_labels)),
      'bounds': str(get_bounds(all_labels))
    }
    file.write(json.dumps(stats))

  # Plot errors
  error_fig = plt.figure(num=None, figsize=(16, 9), dpi=300)
  plt.subplot(3, 1, 1)
  plt.plot(test_labels[:, 0], 'b')
  plt.plot(predictions[:, 0], 'g')
  plt.ylabel("X (m)")

  plt.subplot(3, 1, 2)
  plt.plot(test_labels[:, 1], 'b')
  plt.plot(predictions[:, 1], 'g')
  plt.ylabel("Y (m)")

  plt.subplot(3, 1, 3)
  plt.plot(indv_distances, 'g')
  plt.ylabel("Distance Error (m)")

  error_graph = os.path.join(model_dir, model_name + "_graph_error.png")
  error_fig.savefig(error_graph)

  # Plot point distribution
  distrib_graph = os.path.join(model_dir,
                               model_name + "_graph_point_distrib.png")
  graph_point_distribution(all_labels, save_file=distrib_graph)


def main():
  parser = argparse.ArgumentParser(description='Train the NN')

  parser.add_argument('--out_model_name',
                      action="store",
                      dest='out_model_name',
                      help='''Name of the output model. NOTE: A hash of the 
                      training parameters will be appended to the end of the 
                      name.''',
                      required=True)
  parser.add_argument('--out_dir',
                      action="store",
                      dest='out_dir',
                      help='Path to write output files and model checkpoints',
                      required=True)
  parser.add_argument('--session_dir',
                      action="store",
                      dest='session_dir',
                      help='Location of data to use for training',
                      required=True)
  parser.add_argument('--num_sensors',
                      action="store",
                      dest="num_sensors",
                      type=int,
                      help='Number of sensors',
                      required=True)
  parser.add_argument('--ids',
                      nargs='+',
                      dest='ids',
                      help='Session ids',
                      required=True)
  parser.add_argument('--epochs',
                      action="store",
                      dest="epochs",
                      default=10000,
                      type=int,
                      help='Number of epochs')

  args = parser.parse_args()

  train_model(model_name=args.out_model_name,
              model_dir=args.out_dir,
              session_dir=args.session_dir,
              session_ids=args.ids,
              num_sensors=args.num_sensors,
              num_epochs=args.epochs)


if __name__ == "__main__":
  main()
