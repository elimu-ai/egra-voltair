#include "tr.h"

TR::TR(QObject *parent) : QObject(parent)
{
}

void TR::loadDictionary(const QString &language)
{
    //TODO: implement loadDictionary
}

QString TR::value(const QString &key)
{
    return _dictionary.contains(key) ? _dictionary[key] : key;
}
