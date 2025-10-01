#!/bin/sh

export POSIXLY_CORRECT=1
# shellcheck disable=SC3040
(set +o | grep -q posix) && set -o posix

SCRIPT_PATH="$0"
while APP_HOME="${SCRIPT_PATH%"${SCRIPT_PATH##*/}"}"; [ -h "${SCRIPT_PATH}" ]; do
  SCRIPT_PATH="$(readlink "${SCRIPT_PATH}")"
  case "${SCRIPT_PATH}" in
    /*) ;;
     *) SCRIPT_PATH="${APP_HOME}${SCRIPT_PATH}"
  esac
done

APP_HOME="$(cd -P "${APP_HOME}/.." > /dev/null && pwd -L)" || exit
unset JAVA_CMD

if [ -x "${APP_HOME}/conf/defaults" ]; then
  # shellcheck disable=SC1090
  . "${APP_HOME}/conf/defaults"
fi

if [ -n "${JAVA_CMD}" ]; then
  true
elif [ -n "${JAVA_HOME}" ]; then
  if [ -x "${JAVA_HOME}/bin/java" ]; then
    JAVA_CMD="${JAVA_HOME}/bin/java"
    export JAVA_HOME
  else
    echo "JAVA_HOME is set to an invalid directory: ${JAVA_HOME}"
    echo "Please set the JAVA_HOME variable in your environment to a valid java installation."
    exit 1
  fi
else
  JAVA_CMD="java"
  if ! command -v java > /dev/null 2>&1; then
    echo "JAVA_HOME is not set and no java command could be found."
    echo "Please set the JAVA_HOME variable in your environment to a valid java installation."
    exit 1
  fi
fi

set -- "--module" @@@MAIN_MODULE@@@ "$@"
if [ -f "${APP_HOME}/conf/jvm_args.txt" ]; then
  set -- "@${APP_HOME}/conf/jvm_args.txt" "$@"
fi
set -- "--module-path" @@@MODULE_PATH@@@ "--add-modules" "ALL-DEFAULT" "--add-modules" "ALL-MODULE-PATH" @@@JVM_ARGS@@@ "$@"

exec "${JAVA_CMD}" "$@"
