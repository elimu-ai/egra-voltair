#ifndef TR_H
#define TR_H

#include <QObject>
#include <QMap>

class TR : public QObject
{
    Q_OBJECT
public:
    explicit TR(QObject *parent = 0);
    static void loadDictionary(const QString &language);
    static QString value(const QString &key);
    static QString operator()(const QString &key);
private:
    QMap<QString, QString> _dictionary;
};

#endif // TR_H
